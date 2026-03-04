package ru.tuganov.dto;

public record AddEmailResponse(
        String action,
        int webCount,
        int telegramCount
) {}
