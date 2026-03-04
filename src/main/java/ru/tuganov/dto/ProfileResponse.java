package ru.tuganov.dto;

import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String email,
        Long telegramId,
        boolean emailConfirmed,
        boolean telegramLinked,
        boolean emailNotificationsEnabled,
        Instant createdAt,
        AlertStats alertStats
) {}
