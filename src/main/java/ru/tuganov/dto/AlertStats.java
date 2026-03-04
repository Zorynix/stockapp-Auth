package ru.tuganov.dto;

public record AlertStats(
        long total,
        long buyAlertTriggered,
        long sellAlertTriggered,
        long active
) {}
