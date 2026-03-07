package ru.tuganov.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private static final String SECRET = "test-jwt-secret-key-for-unit-tests-only-must-be-32-chars-min";
    private static final long EXPIRATION_MS = 3600000;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_validUserId_returnsNonEmptyToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.generateToken(userId);
        assertThat(token).isNotBlank();
    }

    @Test
    void validateAndExtractUserId_validToken_returnsUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.generateToken(userId);
        Optional<UUID> result = jwtTokenService.validateAndExtractUserId(token);
        assertThat(result).contains(userId);
    }

    @Test
    void validateAndExtractUserId_expiredToken_returnsEmpty() {
        JwtTokenService shortLived = new JwtTokenService(SECRET, -1000); // already expired
        UUID userId = UUID.randomUUID();
        String token = shortLived.generateToken(userId);
        Optional<UUID> result = jwtTokenService.validateAndExtractUserId(token);
        assertThat(result).isEmpty();
    }

    @Test
    void validateAndExtractUserId_invalidToken_returnsEmpty() {
        Optional<UUID> result = jwtTokenService.validateAndExtractUserId("not-a-jwt");
        assertThat(result).isEmpty();
    }

    @Test
    void validateAndExtractUserId_wrongSignature_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.generateToken(userId);
        JwtTokenService otherService = new JwtTokenService(
                "different-secret-key-for-testing-purposes-32-chars-min", EXPIRATION_MS);
        Optional<UUID> result = otherService.validateAndExtractUserId(token);
        assertThat(result).isEmpty();
    }

    @Test
    void constructor_shortSecret_throwsException() {
        assertThatThrownBy(() -> new JwtTokenService("short", EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32");
    }
}
