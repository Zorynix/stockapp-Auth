package ru.tuganov.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.tuganov.entity.AppUser;
import ru.tuganov.repository.AppUserRepository;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @InjectMocks private AppUserDetailsService service;

    @Test
    void loadUserByUsername_found_returnsDetails() {
        UUID id = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(id);
        when(appUserRepository.findById(id)).thenReturn(Optional.of(user));

        var details = (AppUserDetails) service.loadUserByUsername(id.toString());
        assertThat(details.getUserId()).isEqualTo(id);
        assertThat(details.getAppUser()).isEqualTo(user);
    }

    @Test
    void loadUserByUsername_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(appUserRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername(id.toString()))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_invalidUuid_throws() {
        assertThatThrownBy(() -> service.loadUserByUsername("not-a-uuid"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
