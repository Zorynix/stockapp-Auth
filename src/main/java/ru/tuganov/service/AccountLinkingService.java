package ru.tuganov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.ProfileResponse;
import ru.tuganov.dto.ResolveConflictRequest.ConflictResolution;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.exception.LinkConflictException;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.TrackedInstrumentRepository;
import ru.tuganov.security.TelegramInitDataValidator;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AccountLinkingService {

    private final AppUserRepository appUserRepository;
    private final TrackedInstrumentRepository trackedInstrumentRepository;
    private final TelegramInitDataValidator telegramValidator;
    private final ProfileService profileService;

    @Transactional
    public ProfileResponse linkTelegram(UUID webUserId, String initData) {
        AppUser webUser = findUserOrThrow(webUserId);

        if (webUser.isTelegramLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Telegram уже привязан к этому аккаунту");
        }

        long telegramId = validateAndExtractTelegramId(initData);
        long chatId = telegramId;

        return appUserRepository.findByTelegramId(telegramId)
                .map(telegramUser -> handleExistingTelegramUser(webUser, telegramUser, chatId))
                .orElseGet(() -> linkDirectly(webUser, telegramId, chatId));
    }

    @Transactional
    public ProfileResponse resolveConflict(UUID webUserId, String initData,
                                           ConflictResolution resolution) {
        AppUser webUser = findUserOrThrow(webUserId);

        if (webUser.isTelegramLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Telegram уже привязан к этому аккаунту");
        }

        long telegramId = validateAndExtractTelegramId(initData);
        long chatId = telegramId;

        AppUser telegramUser = appUserRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Telegram-аккаунт не найден. Возможно, конфликт уже разрешён."));

        List<TrackedInstrument> telegramInstruments = trackedInstrumentRepository.findAllByUser(telegramUser);

        switch (resolution) {
            case KEEP_WEB -> {
                log.info("Conflict resolved KEEP_WEB: telegram user {} deleted, {} instruments dropped",
                        telegramUser.getId(), telegramInstruments.size());
            }
            case KEEP_TELEGRAM -> {
                List<TrackedInstrument> webInstruments = trackedInstrumentRepository.findAllByUser(webUser);
                trackedInstrumentRepository.deleteAll(webInstruments);
                migrateInstruments(telegramInstruments, webUser);
                log.info("Conflict resolved KEEP_TELEGRAM: {} web instruments deleted, {} migrated",
                        webInstruments.size(), telegramInstruments.size());
            }
            case MERGE -> {
                migrateInstruments(telegramInstruments, webUser);
                log.info("Conflict resolved MERGE: {} telegram instruments migrated",
                        telegramInstruments.size());
            }
        }

        telegramUser.setTelegramId(null);
        telegramUser.setChatId(null);
        appUserRepository.saveAndFlush(telegramUser);

        webUser.setTelegramId(telegramId);
        webUser.setChatId(chatId);
        appUserRepository.save(webUser);
        appUserRepository.delete(telegramUser);

        log.info("Telegram {} linked to web user {} after conflict resolution ({})",
                telegramId, webUserId, resolution);
        return profileService.getProfile(webUserId);
    }

    private ProfileResponse handleExistingTelegramUser(AppUser webUser, AppUser telegramUser, long chatId) {
        if (telegramUser.getEmail() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Этот Telegram-аккаунт уже привязан к другому email-адресу");
        }

        List<TrackedInstrument> telegramInstruments = trackedInstrumentRepository.findAllByUser(telegramUser);
        List<TrackedInstrument> webInstruments = trackedInstrumentRepository.findAllByUser(webUser);

        if (!telegramInstruments.isEmpty() && !webInstruments.isEmpty()) {
            throw new LinkConflictException(webInstruments.size(), telegramInstruments.size());
        }

        if (!telegramInstruments.isEmpty()) {
            migrateInstruments(telegramInstruments, webUser);
        }
        appUserRepository.delete(telegramUser);

        return linkDirectly(webUser, telegramUser.getTelegramId(), chatId);
    }

    private ProfileResponse linkDirectly(AppUser webUser, long telegramId, long chatId) {
        webUser.setTelegramId(telegramId);
        webUser.setChatId(chatId);
        appUserRepository.save(webUser);
        log.info("Telegram {} linked to web user {}", telegramId, webUser.getId());
        return profileService.getProfile(webUser.getId());
    }

    private void migrateInstruments(List<TrackedInstrument> instruments, AppUser newOwner) {
        for (TrackedInstrument instrument : instruments) {
            instrument.setUser(newOwner);
        }
        trackedInstrumentRepository.saveAll(instruments);
    }

    private long validateAndExtractTelegramId(String initData) {
        if (!telegramValidator.validate(initData)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Невалидные данные Telegram. Попробуйте открыть приложение заново.");
        }
        return telegramValidator.extractUserId(initData)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Не удалось извлечь данные пользователя из Telegram"));
    }

    private AppUser findUserOrThrow(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }
}
