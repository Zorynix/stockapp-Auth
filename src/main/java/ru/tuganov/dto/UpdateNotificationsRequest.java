package ru.tuganov.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationsRequest(
        @NotNull Boolean enabled
) {}
