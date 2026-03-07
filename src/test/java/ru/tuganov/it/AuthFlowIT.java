package ru.tuganov.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.auth.AuthResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.EmailVerificationCode;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.EmailVerificationCodeRepository;
import ru.tuganov.repository.TrackedInstrumentRepository;
import ru.tuganov.security.TelegramInitDataValidator;
import ru.tuganov.service.AppUserService;
import ru.tuganov.service.EmailService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthFlowIT extends BaseIntegrationTest {

    @Autowired
    AppUserService appUserService;
    @Autowired
    AppUserRepository userRepo;
    @Autowired
    EmailVerificationCodeRepository codeRepo;
    @Autowired
    TrackedInstrumentRepository trackedRepo;

    @MockitoBean
    EmailService emailService;
    @MockitoBean
    TelegramInitDataValidator telegramValidator;

    @AfterEach
    void cleanup() {
        trackedRepo.deleteAll();
        codeRepo.deleteAll();
        userRepo.deleteAll();
    }

    @Test
    void register_persistsUserAndSendsVerificationCode() {
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());

        AuthResponse response = appUserService.register("alice@flow.com", "Password1!");

        assertThat(response.email()).isEqualTo("alice@flow.com");
        assertThat(response.emailConfirmed()).isFalse();
        assertThat(userRepo.existsByEmail("alice@flow.com")).isTrue();
        assertThat(codeRepo.count()).isEqualTo(1);
        verify(emailService).sendVerificationCode(eq("alice@flow.com"), anyString());
    }

    @Test
    void register_duplicateEmail_returns409() {
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());
        appUserService.register("dup@flow.com", "Password1!");

        assertThatThrownBy(() -> appUserService.register("dup@flow.com", "Password1!"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void verifyEmail_correctCode_setsEmailConfirmed() {
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());
        appUserService.register("verify@flow.com", "Password1!");

        String code = codeRepo.findAll().get(0).getCode();

        AuthResponse response = appUserService.verifyEmail("verify@flow.com", code);

        assertThat(response.emailConfirmed()).isTrue();
        assertThat(userRepo.findByEmail("verify@flow.com").orElseThrow().isEmailConfirmed()).isTrue();
        assertThat(codeRepo.findAll()).allSatisfy(c -> assertThat(c.isUsed()).isTrue());
    }

    @Test
    void verifyEmail_wrongCode_returns400() {
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());
        appUserService.register("wrong@flow.com", "Password1!");

        assertThatThrownBy(() -> appUserService.verifyEmail("wrong@flow.com", "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        // The @Transactional method rolls back on exception, so attempts remain 0 in DB.
        // The code is still valid and the user can retry.
        assertThat(codeRepo.findAll().get(0).isUsed()).isFalse();
    }

    @Test
    void loginByEmail_correctCredentials_returnsJwt() {
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());
        appUserService.register("login@flow.com", "Password1!");

        AuthResponse response = appUserService.loginByEmail("login@flow.com", "Password1!");

        assertThat(response.token()).isNotBlank();
        assertThat(response.email()).isEqualTo("login@flow.com");
    }

    @Test
    void loginByEmail_wrongPassword_returns401() {
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());
        appUserService.register("pass@flow.com", "Password1!");

        assertThatThrownBy(() -> appUserService.loginByEmail("pass@flow.com", "WrongPassword!"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginByTelegram_newUser_createsUserAndReturnsJwt() {
        when(telegramValidator.validate(anyString())).thenReturn(true);
        when(telegramValidator.extractUserId(anyString())).thenReturn(Optional.of(42L));

        AuthResponse response = appUserService.loginByTelegram("tg-init-data");

        assertThat(response.token()).isNotBlank();
        assertThat(response.telegramId()).isEqualTo(42L);
        assertThat(userRepo.existsByTelegramId(42L)).isTrue();
        assertThat(userRepo.count()).isEqualTo(1);
    }

    @Test
    void loginByTelegram_existingUser_doesNotDuplicate() {
        when(telegramValidator.validate(anyString())).thenReturn(true);
        when(telegramValidator.extractUserId(anyString())).thenReturn(Optional.of(99L));

        appUserService.loginByTelegram("tg-init-data");
        appUserService.loginByTelegram("tg-init-data");

        assertThat(userRepo.count()).isEqualTo(1);
        AppUser user = userRepo.findByTelegramId(99L).orElseThrow();
        assertThat(user.getTelegramId()).isEqualTo(99L);
    }

    @Test
    void loginByTelegram_invalidData_returns401() {
        when(telegramValidator.validate(anyString())).thenReturn(false);

        assertThatThrownBy(() -> appUserService.loginByTelegram("bad-data"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void resendVerificationCode_sendsNewCode() {
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());
        doNothing().when(emailService).sendNewVerificationCode(anyString(), anyString());
        appUserService.register("resend@flow.com", "Password1!");

        appUserService.resendVerificationCode("resend@flow.com");

        verify(emailService).sendNewVerificationCode(eq("resend@flow.com"), anyString());
        assertThat(codeRepo.findAll().stream().filter(c -> !c.isUsed()).count()).isEqualTo(1);
    }
}
