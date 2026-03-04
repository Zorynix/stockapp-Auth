package ru.tuganov.exception;

import lombok.Getter;

@Getter
public class LinkConflictException extends RuntimeException {

    private final int webInstrumentsCount;
    private final int telegramInstrumentsCount;

    public LinkConflictException(int webInstrumentsCount, int telegramInstrumentsCount) {
        super("Оба аккаунта содержат отслеживаемые инструменты — выберите стратегию объединения");
        this.webInstrumentsCount = webInstrumentsCount;
        this.telegramInstrumentsCount = telegramInstrumentsCount;
    }
}
