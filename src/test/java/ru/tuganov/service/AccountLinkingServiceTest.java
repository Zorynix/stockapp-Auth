package ru.tuganov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.AlertStats;
import ru.tuganov.dto.ProfileResponse;
import ru.tuganov.dto.ResolveConflictRequest.ConflictResolution;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.exception.LinkConflictException;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.TrackedInstrumentRepository;
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
class AccountLinkingServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private TrackedInstrumentRepository trackedInstrumentRepository;
    @Mock private TelegramInitDataValidator telegramValidator;
    @Mock private ProfileService profileService;
    @InjectMocks private AccountLinkingService service;

    @Test
    void linkTelegram_directLink_noConflict() {
        UUID webId = UUID.randomUUID();
        AppUser webUser = webUser(webId);
        when(appUserRepository.findById(webId)).thenReturn(Optional.of(webUser));
        when(telegramValidator.validate("initData")).thenReturn(true);
        when(telegramValidator.extractUserId("initData")).thenReturn(Optional.of(555L));
        when(appUserRepository.findByTelegramId(555L)).thenReturn(Optional.empty());
        when(appUserRepository.save(any())).thenReturn(webUser);
        ProfileResponse profile = mockProfile(webId, 555L);
        when(profileService.getProfile(webId)).thenReturn(profile);

        ProfileResponse result = service.linkTelegram(webId, "initData");

        assertThat(result.telegramId()).isEqualTo(555L);
    }

    @Test
    void linkTelegram_alreadyLinked_throws409() {
        UUID webId = UUID.randomUUID();
        AppUser webUser = webUser(webId);
        webUser.setTelegramId(123L);
        when(appUserRepository.findById(webId)).thenReturn(Optional.of(webUser));

        assertThatThrownBy(() -> service.linkTelegram(webId, "initData"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(409);
    }

    @Test
    void linkTelegram_invalidInitData_throws401() {
        UUID webId = UUID.randomUUID();
        AppUser webUser = webUser(webId);
        when(appUserRepository.findById(webId)).thenReturn(Optional.of(webUser));
        when(telegramValidator.validate("bad")).thenReturn(false);

        assertThatThrownBy(() -> service.linkTelegram(webId, "bad"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(401);
    }

    @Test
    void linkTelegram_bothUsersHaveInstruments_throwsLinkConflict() {
        UUID webId = UUID.randomUUID();
        AppUser webUser = webUser(webId);
        AppUser tgUser = tgUser(UUID.randomUUID(), 555L);
        when(appUserRepository.findById(webId)).thenReturn(Optional.of(webUser));
        when(telegramValidator.validate("initData")).thenReturn(true);
        when(telegramValidator.extractUserId("initData")).thenReturn(Optional.of(555L));
        when(appUserRepository.findByTelegramId(555L)).thenReturn(Optional.of(tgUser));
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(List.of(ti(tgUser)));
        when(trackedInstrumentRepository.findAllByUser(webUser)).thenReturn(List.of(ti(webUser)));

        assertThatThrownBy(() -> service.linkTelegram(webId, "initData"))
                .isInstanceOf(LinkConflictException.class);
    }

    @Test
    void linkTelegram_onlyTelegramHasInstruments_migratesAndLinks() {
        UUID webId = UUID.randomUUID();
        AppUser webUser = webUser(webId);
        AppUser tgUser = tgUser(UUID.randomUUID(), 555L);
        when(appUserRepository.findById(webId)).thenReturn(Optional.of(webUser));
        when(telegramValidator.validate("initData")).thenReturn(true);
        when(telegramValidator.extractUserId("initData")).thenReturn(Optional.of(555L));
        when(appUserRepository.findByTelegramId(555L)).thenReturn(Optional.of(tgUser));
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(List.of(ti(tgUser)));
        when(trackedInstrumentRepository.findAllByUser(webUser)).thenReturn(List.of());
        when(appUserRepository.save(any())).thenReturn(webUser);
        when(profileService.getProfile(webId)).thenReturn(mockProfile(webId, 555L));

        service.linkTelegram(webId, "initData");

        verify(trackedInstrumentRepository).saveAll(anyList());
        verify(appUserRepository).delete(tgUser);
    }

    @Test
    void resolveConflict_keepWeb_deletesTelegramUserAndInstruments() {
        UUID webId = UUID.randomUUID();
        AppUser webUser = webUser(webId);
        AppUser tgUser = tgUser(UUID.randomUUID(), 555L);
        when(appUserRepository.findById(webId)).thenReturn(Optional.of(webUser));
        when(telegramValidator.validate("initData")).thenReturn(true);
        when(telegramValidator.extractUserId("initData")).thenReturn(Optional.of(555L));
        when(appUserRepository.findByTelegramId(555L)).thenReturn(Optional.of(tgUser));
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(List.of());
        when(appUserRepository.saveAndFlush(any())).thenReturn(tgUser);
        when(appUserRepository.save(any())).thenReturn(webUser);
        when(profileService.getProfile(webId)).thenReturn(mockProfile(webId, 555L));

        service.resolveConflict(webId, "initData", ConflictResolution.KEEP_WEB);

        verify(appUserRepository).delete(tgUser);
    }

    @Test
    void resolveConflict_keepTelegram_deletesWebInstrumentsAndMigrates() {
        UUID webId = UUID.randomUUID();
        AppUser webUser = webUser(webId);
        AppUser tgUser = tgUser(UUID.randomUUID(), 555L);
        TrackedInstrument webInst = ti(webUser);
        TrackedInstrument tgInst = ti(tgUser);
        when(appUserRepository.findById(webId)).thenReturn(Optional.of(webUser));
        when(telegramValidator.validate("initData")).thenReturn(true);
        when(telegramValidator.extractUserId("initData")).thenReturn(Optional.of(555L));
        when(appUserRepository.findByTelegramId(555L)).thenReturn(Optional.of(tgUser));
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(List.of(tgInst));
        when(trackedInstrumentRepository.findAllByUser(webUser)).thenReturn(List.of(webInst));
        when(appUserRepository.saveAndFlush(any())).thenReturn(tgUser);
        when(appUserRepository.save(any())).thenReturn(webUser);
        when(profileService.getProfile(webId)).thenReturn(mockProfile(webId, 555L));

        service.resolveConflict(webId, "initData", ConflictResolution.KEEP_TELEGRAM);

        verify(trackedInstrumentRepository).deleteAll(List.of(webInst));
        verify(trackedInstrumentRepository).saveAll(anyList());
    }

    @Test
    void resolveConflict_merge_migratesAllInstruments() {
        UUID webId = UUID.randomUUID();
        AppUser webUser = webUser(webId);
        AppUser tgUser = tgUser(UUID.randomUUID(), 555L);
        TrackedInstrument tgInst = ti(tgUser);
        when(appUserRepository.findById(webId)).thenReturn(Optional.of(webUser));
        when(telegramValidator.validate("initData")).thenReturn(true);
        when(telegramValidator.extractUserId("initData")).thenReturn(Optional.of(555L));
        when(appUserRepository.findByTelegramId(555L)).thenReturn(Optional.of(tgUser));
        when(trackedInstrumentRepository.findAllByUser(tgUser)).thenReturn(List.of(tgInst));
        when(appUserRepository.saveAndFlush(any())).thenReturn(tgUser);
        when(appUserRepository.save(any())).thenReturn(webUser);
        when(profileService.getProfile(webId)).thenReturn(mockProfile(webId, 555L));

        service.resolveConflict(webId, "initData", ConflictResolution.MERGE);

        verify(trackedInstrumentRepository).saveAll(anyList());
    }

    // helpers

    private AppUser webUser(UUID id) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail("web@test.com");
        return u;
    }

    private AppUser tgUser(UUID id, long telegramId) {
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

    private ProfileResponse mockProfile(UUID id, Long telegramId) {
        return new ProfileResponse(id, "web@test.com", telegramId,
                true, telegramId != null, false, Instant.now(),
                new AlertStats(0, 0, 0, 0));
    }
}
