package ru.tuganov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.AlertStats;
import ru.tuganov.dto.ProfileResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.TrackedInstrumentRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProfileService {

    private final AppUserRepository appUserRepository;
    private final TrackedInstrumentRepository trackedInstrumentRepository;

    public ProfileResponse getProfile(UUID userId) {
        AppUser user = findOrThrow(userId);
        return buildResponse(user);
    }

    @Transactional
    public ProfileResponse updateEmailNotifications(UUID userId, boolean enabled) {
        AppUser user = findOrThrow(userId);

        if (enabled && !user.isEmailConfirmed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Нельзя включить email-уведомления: адрес не подтверждён");
        }

        user.setEmailNotificationsEnabled(enabled);
        user = appUserRepository.save(user);
        log.info("Email notifications {} for user {}", enabled ? "enabled" : "disabled", userId);
        return buildResponse(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        AppUser user = findOrThrow(userId);
        appUserRepository.delete(user);
        log.info("Account deleted: {}", userId);
    }

    private AppUser findOrThrow(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }

    private ProfileResponse buildResponse(AppUser user) {
        List<TrackedInstrument> instruments = trackedInstrumentRepository.findAllByUser(user);
        AlertStats stats = computeStats(instruments);
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getTelegramId(),
                user.isEmailConfirmed(),
                user.isTelegramLinked(),
                user.isEmailNotificationsEnabled(),
                user.getCreatedAt(),
                stats
        );
    }

    private AlertStats computeStats(List<TrackedInstrument> instruments) {
        long total = instruments.size();
        long buyTriggered = instruments.stream().filter(TrackedInstrument::isBuyAlertSent).count();
        long sellTriggered = instruments.stream().filter(TrackedInstrument::isSellAlertSent).count();
        long active = instruments.stream()
                .filter(i -> !i.isBuyAlertSent() && !i.isSellAlertSent())
                .count();
        return new AlertStats(total, buyTriggered, sellTriggered, active);
    }
}
