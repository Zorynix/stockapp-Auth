package ru.tuganov.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.tuganov.dto.AlertStats;
import ru.tuganov.dto.ProfileResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.security.AppUserDetails;
import ru.tuganov.security.AppUserDetailsService;
import ru.tuganov.security.JwtTokenService;
import ru.tuganov.service.AccountLinkingService;
import ru.tuganov.service.AppUserService;
import ru.tuganov.service.ProfileService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ProfileControllerTest {

    @Autowired private WebApplicationContext wac;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private ProfileService profileService;
    @MockitoBean private AccountLinkingService accountLinkingService;
    @MockitoBean private AppUserService appUserService;
    @MockitoBean private JwtTokenService jwtTokenService;
    @MockitoBean private AppUserDetailsService appUserDetailsService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private AppUserDetails userDetails(UUID id) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail("test@mail.com");
        return new AppUserDetails(u);
    }

    private ProfileResponse profileResponse(UUID id) {
        return new ProfileResponse(id, "test@mail.com", null,
                true, false, true, Instant.now(),
                new AlertStats(0, 0, 0, 0));
    }

    @Test
    void getProfile_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProfile_authenticated_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(profileService.getProfile(id)).thenReturn(profileResponse(id));

        mvc.perform(get("/api/profile")
                        .with(user(userDetails(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void updateNotifications_authenticated_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(profileService.updateEmailNotifications(id, true)).thenReturn(profileResponse(id));

        mvc.perform(patch("/api/profile/notifications")
                        .with(user(userDetails(id)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccount_authenticated_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(profileService).deleteAccount(id);

        mvc.perform(delete("/api/profile")
                        .with(user(userDetails(id))))
                .andExpect(status().isNoContent());
    }

    @Test
    void linkTelegram_authenticated_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(accountLinkingService.linkTelegram(eq(id), anyString())).thenReturn(profileResponse(id));

        mvc.perform(post("/api/profile/link-telegram")
                        .with(user(userDetails(id)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("initData", "tg-data"))))
                .andExpect(status().isOk());
    }
}
