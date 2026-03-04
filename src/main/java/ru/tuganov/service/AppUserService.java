package ru.tuganov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.AddEmailResponse;
import ru.tuganov.dto.ResolveConflictRequest.ConflictResolution;
import ru.tuganov.dto.auth.AuthResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.EmailVerificationCode;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.EmailVerificationCodeRepository;
import ru.tuganov.repository.TrackedInstrumentRepository;
import ru.tuganov.security.JwtTokenService;
import ru.tuganov.security.TelegramInitDataValidator;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AppUserService {

    private static final int OTP_TTL_MINUTES = 15;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final EmailVerificationCodeRepository verificationCodeRepository;
    private final TrackedInstrumentRepository trackedInstrumentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final EmailService emailService;
    private final TelegramInitDataValidator telegramValidator;

    @Transactional
    public AuthResponse register(String email, String rawPassword) {
        String normalizedEmail = email.strip().toLowerCase();

        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email уже зарегистрирован. Войдите или используйте другой адрес.");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        AppUser user = AppUser.fromEmail(normalizedEmail, passwordHash);
        user = appUserRepository.save(user);

        sendVerificationCode(user, normalizedEmail, false);

        log.info("New user registered via email: {}", normalizedEmail);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse verifyEmail(String email, String code) {
        String normalizedEmail = email.strip().toLowerCase();
        AppUser user = findUserByEmailOrThrow(normalizedEmail);

        if (user.isEmailConfirmed()) {
            return buildAuthResponse(user);
        }

        EmailVerificationCode otp = verificationCodeRepository
                .findValidCodeForUser(user, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Код недействителен или истёк"));

        if (!MessageDigest.isEqual(otp.getCode().getBytes(), code.getBytes())) {
            otp.incrementAttempts();
            verificationCodeRepository.save(otp);
            if (otp.isExhausted()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Превышено число попыток. Запросите новый код.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный код подтверждения");
        }

        verificationCodeRepository.invalidateAllForUser(user);
        user.setEmailConfirmed(true);
        user = appUserRepository.save(user);

        log.info("Email confirmed for user: {}", user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginByEmail(String email, String rawPassword) {
        String normalizedEmail = email.strip().toLowerCase();

        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Неверный email или пароль"));

        log.info("User logged in via email: {}", user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginByTelegram(String initData) {
        if (!telegramValidator.validate(initData)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Невалидные данные Telegram. Попробуйте открыть приложение заново.");
        }

        long telegramId = telegramValidator.extractUserId(initData)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Не удалось извлечь данные пользователя из Telegram"));

        long chatId = telegramId;

        AppUser user = appUserRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    var newUser = AppUser.fromTelegram(telegramId, chatId);
                    log.info("Auto-registering Telegram user: {}", telegramId);
                    return appUserRepository.save(newUser);
                });

        if (!Long.valueOf(chatId).equals(user.getChatId())) {
            user.setChatId(chatId);
            appUserRepository.save(user);
        }

        log.info("User logged in via Telegram: {} (appUserId={})", telegramId, user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public void resendVerificationCode(String email) {
        String normalizedEmail = email.strip().toLowerCase();
        appUserRepository.findByEmail(normalizedEmail)
                .filter(u -> !u.isEmailConfirmed())
                .ifPresent(user -> {
                    verificationCodeRepository.invalidateAllForUser(user);
                    sendVerificationCode(user, normalizedEmail, true);
                    log.info("Verification code resent to: {}", normalizedEmail);
                });
    }

    @Transactional
    public AddEmailResponse addEmail(UUID currentUserId, String email, String rawPassword) {
        String normalizedEmail = email.strip().toLowerCase();
        AppUser currentUser = findUserOrThrow(currentUserId);

        if (currentUser.getEmail() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "К аккаунту уже привязана почта");
        }

        Optional<AppUser> existingOpt = appUserRepository.findByEmail(normalizedEmail);

        if (existingOpt.isEmpty()) {
            currentUser.setEmail(normalizedEmail);
            currentUser.setPasswordHash(passwordEncoder.encode(rawPassword));
            currentUser.setEmailConfirmed(false);
            appUserRepository.save(currentUser);
            sendVerificationCode(currentUser, normalizedEmail, false);

            int telegramCount = trackedInstrumentRepository.findAllByUser(currentUser).size();
            log.info("Telegram user {} initiated email registration: {}", currentUserId, normalizedEmail);
            return new AddEmailResponse("VERIFY", 0, telegramCount);
        } else {
            AppUser webUser = existingOpt.get();
            if (webUser.getPasswordHash() == null
                    || !passwordEncoder.matches(rawPassword, webUser.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный пароль");
            }
            sendVerificationCode(webUser, normalizedEmail, false);

            int webCount = trackedInstrumentRepository.findAllByUser(webUser).size();
            int telegramCount = trackedInstrumentRepository.findAllByUser(currentUser).size();
            log.info("Telegram user {} initiating link with web account {}", currentUserId, webUser.getId());
            return new AddEmailResponse("LINK", webCount, telegramCount);
        }
    }

    @Transactional
    public AuthResponse verifyAddEmail(UUID currentUserId, String email, String code,
                                       ConflictResolution resolution) {
        String normalizedEmail = email.strip().toLowerCase();
        AppUser currentUser = findUserOrThrow(currentUserId);

        boolean isLinkScenario;
        AppUser otpOwner;
        if (normalizedEmail.equals(currentUser.getEmail())) {
            otpOwner = currentUser;
            isLinkScenario = false;
        } else {
            otpOwner = appUserRepository.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Аккаунт с таким email не найден"));
            isLinkScenario = true;
            if (resolution == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Необходимо выбрать стратегию объединения");
            }
        }

        EmailVerificationCode otp = verificationCodeRepository
                .findValidCodeForUser(otpOwner, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Код недействителен или истёк"));

        if (!MessageDigest.isEqual(otp.getCode().getBytes(), code.getBytes())) {
            otp.incrementAttempts();
            verificationCodeRepository.save(otp);
            if (otp.isExhausted()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Превышено число попыток. Запросите новый код.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный код подтверждения");
        }
        verificationCodeRepository.invalidateAllForUser(otpOwner);

        if (!isLinkScenario) {
            currentUser.setEmailConfirmed(true);
            currentUser = appUserRepository.save(currentUser);
            log.info("Email confirmed for Telegram user: {}", currentUserId);
            return buildAuthResponse(currentUser);
        } else {
            AppUser webUser = otpOwner;
            List<TrackedInstrument> telegramInstruments = trackedInstrumentRepository.findAllByUser(currentUser);
            List<TrackedInstrument> webInstruments = trackedInstrumentRepository.findAllByUser(webUser);

            switch (resolution) {
                case KEEP_WEB -> {
                    trackedInstrumentRepository.deleteAll(telegramInstruments);
                    log.info("Conflict KEEP_WEB: {} telegram instruments deleted", telegramInstruments.size());
                }
                case KEEP_TELEGRAM -> {
                    trackedInstrumentRepository.deleteAll(webInstruments);
                    migrateInstruments(telegramInstruments, webUser);
                    log.info("Conflict KEEP_TELEGRAM: {} web instruments deleted, {} migrated",
                            webInstruments.size(), telegramInstruments.size());
                }
                case MERGE -> {
                    migrateInstruments(telegramInstruments, webUser);
                    log.info("Conflict MERGE: {} telegram instruments migrated", telegramInstruments.size());
                }
            }

            long telegramId = currentUser.getTelegramId();
            long chatId = currentUser.getChatId();

            currentUser.setTelegramId(null);
            currentUser.setChatId(null);
            appUserRepository.saveAndFlush(currentUser);

            webUser.setTelegramId(telegramId);
            webUser.setChatId(chatId);
            webUser.setEmailConfirmed(true);
            appUserRepository.save(webUser);

            appUserRepository.delete(currentUser);
            log.info("Telegram user {} merged into web user {} ({})", currentUserId, webUser.getId(), resolution);

            return buildAuthResponse(webUser);
        }
    }

    private void migrateInstruments(List<TrackedInstrument> instruments, AppUser newOwner) {
        for (TrackedInstrument instrument : instruments) {
            instrument.setUser(newOwner);
        }
        trackedInstrumentRepository.saveAll(instruments);
    }

    private AppUser findUserOrThrow(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }

    private void sendVerificationCode(AppUser user, String email, boolean isResend) {
        String code = generateOtpCode();
        EmailVerificationCode otp = EmailVerificationCode.create(user, code, OTP_TTL_MINUTES);
        verificationCodeRepository.save(otp);

        if (isResend) {
            emailService.sendNewVerificationCode(email, code);
        } else {
            emailService.sendVerificationCode(email, code);
        }
    }

    private String generateOtpCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private AppUser findUserByEmailOrThrow(String email) {
        return appUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Пользователь с таким email не найден"));
    }

    private AuthResponse buildAuthResponse(AppUser user) {
        String token = jwtTokenService.generateToken(user.getId());
        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getTelegramId(),
                user.isEmailConfirmed(),
                user.isTelegramLinked(),
                user.isEmailNotificationsEnabled()
        );
    }
}
