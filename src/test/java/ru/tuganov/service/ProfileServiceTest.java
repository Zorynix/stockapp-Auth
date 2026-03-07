package ru.tuganov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.ProfileResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.TrackedInstrumentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private TrackedInstrumentRepository trackedInstrumentRepository;
    @InjectMocks private ProfileService profileService;

    @Test
    void getProfile_existingUser_returnsProfile() {
        UUID id = UUID.randomUUID();
        AppUser u = user(id, "test@mail.com");
        when(appUserRepository.findById(id)).thenReturn(Optional.of(u));
        when(trackedInstrumentRepository.findAllByUser(u)).thenReturn(List.of());

        ProfileResponse p = profileService.getProfile(id);

        assertThat(p.id()).isEqualTo(id);
        assertThat(p.email()).isEqualTo("test@mail.com");
        assertThat(p.alertStats().total()).isZero();
    }

    @Test
    void getProfile_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(appUserRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(id))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(404);
    }

    @Test
    void updateEmailNotifications_enable_withConfirmedEmail_succeeds() {
        UUID id = UUID.randomUUID();
        AppUser u = user(id, "test@mail.com");
        u.setEmailConfirmed(true);
        when(appUserRepository.findById(id)).thenReturn(Optional.of(u));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(trackedInstrumentRepository.findAllByUser(any())).thenReturn(List.of());

        profileService.updateEmailNotifications(id, true);

        assertThat(u.isEmailNotificationsEnabled()).isTrue();
    }

    @Test
    void updateEmailNotifications_enable_withoutConfirmedEmail_throws400() {
        UUID id = UUID.randomUUID();
        AppUser u = user(id, "test@mail.com");
        u.setEmailConfirmed(false);
        when(appUserRepository.findById(id)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> profileService.updateEmailNotifications(id, true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(400);
    }

    @Test
    void updateEmailNotifications_disable_alwaysSucceeds() {
        UUID id = UUID.randomUUID();
        AppUser u = user(id, "test@mail.com");
        u.setEmailNotificationsEnabled(true);
        when(appUserRepository.findById(id)).thenReturn(Optional.of(u));
        when(appUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(trackedInstrumentRepository.findAllByUser(any())).thenReturn(List.of());

        profileService.updateEmailNotifications(id, false);

        assertThat(u.isEmailNotificationsEnabled()).isFalse();
    }

    @Test
    void deleteAccount_existingUser_deletesIt() {
        UUID id = UUID.randomUUID();
        AppUser u = user(id, "test@mail.com");
        when(appUserRepository.findById(id)).thenReturn(Optional.of(u));

        profileService.deleteAccount(id);

        verify(appUserRepository).delete(u);
    }

    @Test
    void getProfile_alertStats_calculatedCorrectly() {
        UUID id = UUID.randomUUID();
        AppUser u = user(id, "stats@mail.com");
        TrackedInstrument active = ti(u, false, false);
        TrackedInstrument buyTriggered = ti(u, true, false);
        TrackedInstrument sellTriggered = ti(u, false, true);
        when(appUserRepository.findById(id)).thenReturn(Optional.of(u));
        when(trackedInstrumentRepository.findAllByUser(u))
                .thenReturn(List.of(active, buyTriggered, sellTriggered));

        ProfileResponse p = profileService.getProfile(id);

        assertThat(p.alertStats().total()).isEqualTo(3);
        assertThat(p.alertStats().buyAlertTriggered()).isEqualTo(1);
        assertThat(p.alertStats().sellAlertTriggered()).isEqualTo(1);
        assertThat(p.alertStats().active()).isEqualTo(1);
    }

    // helpers

    private AppUser user(UUID id, String email) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private TrackedInstrument ti(AppUser owner, boolean buySent, boolean sellSent) {
        TrackedInstrument t = new TrackedInstrument();
        t.setUser(owner);
        t.setFigi("BBG000B9XRY4");
        t.setInstrumentName("Test");
        t.setBuyPrice(BigDecimal.valueOf(100));
        t.setSellPrice(BigDecimal.valueOf(200));
        t.setBuyAlertSent(buySent);
        t.setSellAlertSent(sellSent);
        return t;
    }
}
