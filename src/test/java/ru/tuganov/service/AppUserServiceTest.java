package ru.tuganov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.AddEmailResponse;
import ru.tuganov.dto.ResolveConflictRequest;
import ru.tuganov.dto.auth.AuthResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.EmailVerificationCode;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.EmailVerificationCodeRepository;
import ru.tuganov.repository.TrackedInstrumentRepository;
import ru.tuganov.security.JwtTokenService;
import ru.tuganov.security.TelegramInitDataValidator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private EmailVerificationCodeRepository verificationCodeRepository;
    @Mock private TrackedInstrumentRepository trackedInstrumentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private EmailService emailService;
    @Mock private TelegramInitDataValidator telegramValidator;

    @InjectMocks private AppUserService appUserService;

    // --- register ---

    @Test
    void register_success_returnsAuthResponse() {
        when(appUserRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("hashed");
        AppUser saved = user(UUID.randomUUID(), "test@example.com");
        when(appUserRepository.save(any(AppUser.class))).thenReturn(saved);
        when(verificationCodeRepository.save(any())).thenReturn(mock(EmailVerificationCode.class));
        when(jwtTokenService.generateToken(saved.getId())).thenReturn("jwt-token");

        AuthResponse result = appUserService.register("test@example.com", "password");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.email()).isEqualTo("test@example.com");
        verify(emailService).sendVerificationCode(eq("test@example.com"), anyString());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(appUserRepository.existsByEmail("dup@example.com")).thenReturn(true);
        assertThatThrownBy(() -> appUserService.register("dup@example.com", "password"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(409);
    }

    @Test
    void register_normalizesEmail() {
        when(appUserRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        AppUser saved = user(UUID.randomUUID(), "test@example.com");
        when(appUserRepository.save(any())).thenReturn(saved);
        when(verificationCodeRepository.save(any())).thenReturn(mock(EmailVerificationCode.class));
        when(jwtTokenService.generateToken(any())).thenReturn("token");

        appUserService.register("  TEST@Example.COM  ", "password");

        verify(appUserRepository).existsByEmail("test@example.com");
    }

    // --- verifyEmail ---

    @Test
    void verifyEmail_correctCode_confirmsEmail() {
        AppUser u = user(UUID.randomUUID(), "test@example.com");
        EmailVerificationCode otp = EmailVerificationCode.create(u, "123456", 15);
        when(appUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(u));
        when(verificationCodeRepository.findValidCodeForUser(eq(u), any(Instant.class)))
                .thenReturn(Optional.of(otp));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenService.generateToken(u.getId())).thenReturn("token");

        AuthResponse result = appUserService.verifyEmail("test@example.com", "123456");

        assertThat(result.emailConfirmed()).isTrue();
        verify(verificationCodeRepository).invalidateAllForUser(u);
    }

    @Test
    void verifyEmail_wrongCode_throwsBadRequest() {
        AppUser u = user(UUID.randomUUID(), "test@example.com");
        EmailVerificationCode otp = EmailVerificationCode.create(u, "123456", 15);
        when(appUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(u));
        when(verificationCodeRepository.findValidCodeForUser(eq(u), any())).thenReturn(Optional.of(otp));
        when(verificationCodeRepository.save(any())).thenReturn(otp);

        assertThatThrownBy(() -> appUserService.verifyEmail("test@example.com", "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(400);
    }

    @Test
    void verifyEmail_expiredCode_throwsBadRequest() {
        AppUser u = user(UUID.randomUUID(), "test@example.com");
        when(appUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(u));
        when(verificationCodeRepository.findValidCodeForUser(eq(u), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserService.verifyEmail("test@example.com", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(400);
    }

    @Test
    void verifyEmail_exhaustedAttempts_throwsTooManyRequests() {
        AppUser u = user(UUID.randomUUID(), "test@example.com");
        EmailVerificationCode otp = EmailVerificationCode.create(u, "123456", 15);
        for (int i = 0; i < 4; i++) otp.incrementAttempts();
        when(appUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(u));
        when(verificationCodeRepository.findValidCodeForUser(eq(u), any())).thenReturn(Optional.of(otp));
        when(verificationCodeRepository.save(any())).thenReturn(otp);

        assertThatThrownBy(() -> appUserService.verifyEmail("test@example.com", "wrong"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(429);
    }

    // --- loginByEmail ---

    @Test
    void loginByEmail_correctCredentials_returnsAuth() {
        AppUser u = user(UUID.randomUUID(), "test@example.com");
        u.setPasswordHash("hashed");
        when(appUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(jwtTokenService.generateToken(u.getId())).thenReturn("jwt");

        AuthResponse result = appUserService.loginByEmail("test@example.com", "password");

        assertThat(result.token()).isEqualTo("jwt");
    }

    @Test
    void loginByEmail_wrongPassword_throwsUnauthorized() {
        AppUser u = user(UUID.randomUUID(), "test@example.com");
        u.setPasswordHash("hashed");
        when(appUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> appUserService.loginByEmail("test@example.com", "wrong"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(401);
    }

    @Test
    void loginByEmail_unknownEmail_throwsUnauthorized() {
        when(appUserRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserService.loginByEmail("missing@example.com", "pass"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(401);
    }

    // --- loginByTelegram ---

    @Test
    void loginByTelegram_existingUser_returnsAuth() {
        AppUser u = telegramUser(UUID.randomUUID(), 12345L);
        when(telegramValidator.validate("initData")).thenReturn(true);
        when(telegramValidator.extractUserId("initData")).thenReturn(Optional.of(12345L));
        when(appUserRepository.findByTelegramId(12345L)).thenReturn(Optional.of(u));
        when(jwtTokenService.generateToken(u.getId())).thenReturn("jwt");

        AuthResponse result = appUserService.loginByTelegram("initData");

        assertThat(result.token()).isEqualTo("jwt");
        assertThat(result.telegramLinked()).isTrue();
    }

    @Test
    void loginByTelegram_newUser_autoRegisters() {
        when(telegramValidator.validate("initData")).thenReturn(true);
        when(telegramValidator.extractUserId("initData")).thenReturn(Optional.of(99L));
        when(appUserRepository.findByTelegramId(99L)).thenReturn(Optional.empty());
        AppUser saved = telegramUser(UUID.randomUUID(), 99L);
        when(appUserRepository.save(any())).thenReturn(saved);
        when(jwtTokenService.generateToken(saved.getId())).thenReturn("jwt");

        AuthResponse result = appUserService.loginByTelegram("initData");

        assertThat(result.telegramLinked()).isTrue();
    }

    @Test
    void loginByTelegram_invalidInitData_throwsUnauthorized() {
        when(telegramValidator.validate("bad")).thenReturn(false);

        assertThatThrownBy(() -> appUserService.loginByTelegram("bad"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(401);
    }

    // --- resendVerificationCode ---

    @Test
    void resendVerificationCode_unconfirmedUser_sendsNewCode() {
        AppUser u = user(UUID.randomUUID(), "test@example.com");
        when(appUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(u));
        when(verificationCodeRepository.save(any())).thenReturn(mock(EmailVerificationCode.class));

        appUserService.resendVerificationCode("test@example.com");

        verify(verificationCodeRepository).invalidateAllForUser(u);
        verify(emailService).sendNewVerificationCode(eq("test@example.com"), anyString());
    }

    @Test
    void resendVerificationCode_confirmedUser_doesNotSend() {
        AppUser u = user(UUID.randomUUID(), "test@example.com");
        u.setEmailConfirmed(true);
        when(appUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(u));

        appUserService.resendVerificationCode("test@example.com");

        verify(emailService, never()).sendNewVerificationCode(any(), any());
    }

    // --- verifyAddEmail ---

    @Test
    void verifyAddEmail_nonLinkScenario_confirmsEmailAndReturnsAuth() {
        UUID userId = UUID.randomUUID();
        AppUser currentUser = user(userId, "user@example.com");
        EmailVerificationCode otp = EmailVerificationCode.create(currentUser, "123456", 15);

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(verificationCodeRepository.findValidCodeForUser(eq(currentUser), any(Instant.class)))
                .thenReturn(Optional.of(otp));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenService.generateToken(userId)).thenReturn("jwt");

        AuthResponse result = appUserService.verifyAddEmail(userId, "user@example.com", "123456", null);

        assertThat(result.emailConfirmed()).isTrue();
        verify(verificationCodeRepository).invalidateAllForUser(currentUser);
        verify(appUserRepository).save(currentUser);
    }

    @Test
    void verifyAddEmail_nonLinkScenario_noValidOtp_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        AppUser currentUser = user(userId, "user@example.com");

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(verificationCodeRepository.findValidCodeForUser(eq(currentUser), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserService.verifyAddEmail(userId, "user@example.com", "123456", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(400);
    }

    @Test
    void verifyAddEmail_nonLinkScenario_wrongCode_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        AppUser currentUser = user(userId, "user@example.com");
        EmailVerificationCode otp = EmailVerificationCode.create(currentUser, "123456", 15);

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(currentUser));
        when(verificationCodeRepository.findValidCodeForUser(eq(currentUser), any())).thenReturn(Optional.of(otp));
        when(verificationCodeRepository.save(any())).thenReturn(otp);

        assertThatThrownBy(() -> appUserService.verifyAddEmail(userId, "user@example.com", "000000", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(400);
    }

    @Test
    void verifyAddEmail_linkScenario_webEmailNotFound_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        AppUser tgUser = telegramUser(userId, 99L);

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(tgUser));
        when(appUserRepository.findByEmail("web@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserService.verifyAddEmail(userId, "web@example.com", "123456",
                ResolveConflictRequest.ConflictResolution.KEEP_WEB))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(404);
    }

    @Test
    void verifyAddEmail_linkScenario_nullResolution_throwsBadRequest() {
        UUID userId = UUID.randomUUID();
        AppUser tgUser = telegramUser(userId, 99L);
        AppUser webUser = user(UUID.randomUUID(), "web@example.com");

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(tgUser));
        when(appUserRepository.findByEmail("web@example.com")).thenReturn(Optional.of(webUser));

        assertThatThrownBy(() -> appUserService.verifyAddEmail(userId, "web@example.com", "123456", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(400);
    }

    @Test
    void verifyAddEmail_linkScenario_keepWeb_deletesTelegramInstruments() {
        UUID tgId = UUID.randomUUID();
        UUID webId = UUID.randomUUID();
        AppUser tgUser = telegramUser(tgId, 99L);
        AppUser webUser = user(webId, "web@example.com");
        EmailVerificationCode otp = EmailVerificationCode.create(webUser, "123456", 15);
        List<TrackedInstrument> tgInstruments = List.of(ti(tgUser));
        List<TrackedInstrument> webInstruments = List.of(ti(webUser));

        when(appUserRepository.findById(tgId)).thenReturn(Optional.of(tgUser));
        when(appUserRepository.findByEmail("web@example.com")).thenReturn(Optional.of(webUser));
        when(verificationCodeRepository.findValidCodeForUser(eq(webUser), any())).thenReturn(Optional.of(otp));
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(tgInstruments);
        when(trackedInstrumentRepository.findAllByUser(webUser)).thenReturn(webInstruments);
        when(appUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenService.generateToken(webId)).thenReturn("jwt");

        AuthResponse result = appUserService.verifyAddEmail(tgId, "web@example.com", "123456",
                ResolveConflictRequest.ConflictResolution.KEEP_WEB);

        verify(trackedInstrumentRepository).deleteAll(tgInstruments);
        verify(trackedInstrumentRepository, never()).deleteAll(webInstruments);
        verify(appUserRepository).delete(tgUser);
        assertThat(result.token()).isEqualTo("jwt");
    }

    @Test
    void verifyAddEmail_linkScenario_keepTelegram_deletesWebInstrumentsAndMigrates() {
        UUID tgId = UUID.randomUUID();
        UUID webId = UUID.randomUUID();
        AppUser tgUser = telegramUser(tgId, 99L);
        AppUser webUser = user(webId, "web@example.com");
        EmailVerificationCode otp = EmailVerificationCode.create(webUser, "123456", 15);
        List<TrackedInstrument> tgInstruments = List.of(ti(tgUser));
        List<TrackedInstrument> webInstruments = List.of(ti(webUser));

        when(appUserRepository.findById(tgId)).thenReturn(Optional.of(tgUser));
        when(appUserRepository.findByEmail("web@example.com")).thenReturn(Optional.of(webUser));
        when(verificationCodeRepository.findValidCodeForUser(eq(webUser), any())).thenReturn(Optional.of(otp));
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(tgInstruments);
        when(trackedInstrumentRepository.findAllByUser(webUser)).thenReturn(webInstruments);
        when(trackedInstrumentRepository.saveAll(any())).thenReturn(tgInstruments);
        when(appUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenService.generateToken(webId)).thenReturn("jwt");

        appUserService.verifyAddEmail(tgId, "web@example.com", "123456",
                ResolveConflictRequest.ConflictResolution.KEEP_TELEGRAM);

        verify(trackedInstrumentRepository).deleteAll(webInstruments);
        verify(trackedInstrumentRepository, never()).deleteAll(tgInstruments);
        verify(trackedInstrumentRepository).saveAll(tgInstruments);
        verify(appUserRepository).delete(tgUser);
    }

    @Test
    void verifyAddEmail_linkScenario_merge_migratesTelegramInstrumentsWithoutDeletingAny() {
        UUID tgId = UUID.randomUUID();
        UUID webId = UUID.randomUUID();
        AppUser tgUser = telegramUser(tgId, 99L);
        AppUser webUser = user(webId, "web@example.com");
        EmailVerificationCode otp = EmailVerificationCode.create(webUser, "123456", 15);
        List<TrackedInstrument> tgInstruments = List.of(ti(tgUser), ti(tgUser));
        List<TrackedInstrument> webInstruments = List.of(ti(webUser));

        when(appUserRepository.findById(tgId)).thenReturn(Optional.of(tgUser));
        when(appUserRepository.findByEmail("web@example.com")).thenReturn(Optional.of(webUser));
        when(verificationCodeRepository.findValidCodeForUser(eq(webUser), any())).thenReturn(Optional.of(otp));
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(tgInstruments);
        when(trackedInstrumentRepository.findAllByUser(webUser)).thenReturn(webInstruments);
        when(trackedInstrumentRepository.saveAll(any())).thenReturn(tgInstruments);
        when(appUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenService.generateToken(webId)).thenReturn("jwt");

        appUserService.verifyAddEmail(tgId, "web@example.com", "123456",
                ResolveConflictRequest.ConflictResolution.MERGE);

        verify(trackedInstrumentRepository, never()).deleteAll(any(List.class));
        verify(trackedInstrumentRepository).saveAll(tgInstruments);
        verify(appUserRepository).delete(tgUser);
    }

    @Test
    void verifyAddEmail_linkScenario_transfersTelegramIdToWebUser() {
        UUID tgId = UUID.randomUUID();
        UUID webId = UUID.randomUUID();
        AppUser tgUser = telegramUser(tgId, 42L);
        AppUser webUser = user(webId, "web@example.com");
        EmailVerificationCode otp = EmailVerificationCode.create(webUser, "123456", 15);

        when(appUserRepository.findById(tgId)).thenReturn(Optional.of(tgUser));
        when(appUserRepository.findByEmail("web@example.com")).thenReturn(Optional.of(webUser));
        when(verificationCodeRepository.findValidCodeForUser(eq(webUser), any())).thenReturn(Optional.of(otp));
        when(trackedInstrumentRepository.findAllByUser(any())).thenReturn(List.of());
        when(appUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenService.generateToken(webId)).thenReturn("jwt");

        appUserService.verifyAddEmail(tgId, "web@example.com", "123456",
                ResolveConflictRequest.ConflictResolution.KEEP_WEB);

        assertThat(webUser.getTelegramId()).isEqualTo(42L);
        assertThat(webUser.getChatId()).isEqualTo(42L);
        assertThat(webUser.isEmailConfirmed()).isTrue();
        assertThat(tgUser.getTelegramId()).isNull();
    }

    // --- addEmail ---

    @Test
    void addEmail_brandNewEmail_returnsVerify() {
        UUID userId = UUID.randomUUID();
        AppUser tgUser = telegramUser(userId, 123L);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(tgUser));
        when(appUserRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(appUserRepository.save(any())).thenReturn(tgUser);
        when(verificationCodeRepository.save(any())).thenReturn(mock(EmailVerificationCode.class));
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(List.of());

        AddEmailResponse result = appUserService.addEmail(userId, "new@example.com", "pass");

        assertThat(result.action()).isEqualTo("VERIFY");
    }

    @Test
    void addEmail_existingWebEmail_returnsLink() {
        UUID userId = UUID.randomUUID();
        AppUser tgUser = telegramUser(userId, 123L);
        AppUser webUser = user(UUID.randomUUID(), "existing@example.com");
        webUser.setPasswordHash("hashed");
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(tgUser));
        when(appUserRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(webUser));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(verificationCodeRepository.save(any())).thenReturn(mock(EmailVerificationCode.class));
        when(trackedInstrumentRepository.findAllByUser(webUser)).thenReturn(List.of());
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(List.of());

        AddEmailResponse result = appUserService.addEmail(userId, "existing@example.com", "pass");

        assertThat(result.action()).isEqualTo("LINK");
    }

    @Test
    void addEmail_userAlreadyHasEmail_throwsConflict() {
        UUID userId = UUID.randomUUID();
        AppUser u = user(userId, "already@example.com");
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> appUserService.addEmail(userId, "new@example.com", "pass"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(409);
    }

    // helpers

    private AppUser user(UUID id, String email) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private AppUser telegramUser(UUID id, long telegramId) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setTelegramId(telegramId);
        u.setChatId(telegramId);
        return u;
    }

    private TrackedInstrument ti(AppUser owner) {
        TrackedInstrument t = new TrackedInstrument();
        t.setUser(owner);
        t.setFigi("BBG000B9XRY4");
        t.setInstrumentName("Test");
        t.setBuyPrice(BigDecimal.valueOf(100));
        t.setSellPrice(BigDecimal.valueOf(200));
        return t;
    }
}
