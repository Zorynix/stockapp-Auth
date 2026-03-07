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
import ru.tuganov.dto.auth.AuthResponse;
import ru.tuganov.security.AppUserDetailsService;
import ru.tuganov.security.JwtTokenService;
import ru.tuganov.service.AppUserService;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired private WebApplicationContext wac;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private AuthResponse authResponse() {
        return new AuthResponse("token", UUID.randomUUID(), "user@test.com",
                null, false, false, true);
    }

    @Test
    void register_validRequest_returns201() throws Exception {
        when(appUserService.register(anyString(), anyString())).thenReturn(authResponse());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@test.com", "password", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("token"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "not-an-email", "password", "password123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@test.com", "password", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validRequest_returns200() throws Exception {
        when(appUserService.loginByEmail(anyString(), anyString())).thenReturn(authResponse());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@test.com", "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"));
    }

    @Test
    void verify_validRequest_returns200() throws Exception {
        when(appUserService.verifyEmail(anyString(), anyString())).thenReturn(authResponse());

        mvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@test.com", "code", "123456"))))
                .andExpect(status().isOk());
    }

    @Test
    void telegramAuth_validRequest_returns200() throws Exception {
        when(appUserService.loginByTelegram(anyString())).thenReturn(authResponse());

        mvc.perform(post("/api/auth/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("initData", "tg-data"))))
                .andExpect(status().isOk());
    }

    @Test
    void authEndpoints_arePublic() throws Exception {
        when(appUserService.register(anyString(), anyString())).thenReturn(authResponse());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "user@test.com", "password", "password123"))))
                .andExpect(status().isCreated());
    }
}
