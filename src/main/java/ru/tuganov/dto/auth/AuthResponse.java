package ru.tuganov.dto.auth;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String email,
        Long telegramId,
        boolean emailConfirmed,
        boolean telegramLinked,
        boolean emailNotificationsEnabled
) {}
