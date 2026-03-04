# AuthService

Микросервис аутентификации и профилей пользователей.
Является частью микросервисной архитектуры StockApp.

---

## Роль в архитектуре

| Сервис | Порт | Зона ответственности |
|--------|------|----------------------|
| MarketDataService | 8080 | Поиск инструментов, свечи |
| **AuthService** (этот) | 8081 | Аутентификация, профили, JWT |
| AlertService | 8082 | Ценовые алерты, мониторинг, уведомления, отчёты |

Все сервисы (кроме PortfolioService) используют одну PostgreSQL БД (`market_service`).
AuthService **владеет** таблицами `app_users` и `email_verification_codes`.

---

## Функциональность

- **Регистрация и вход по email** с OTP-подтверждением (6-значный код, 5 попыток, 10 минут)
- **Аутентификация через Telegram** — валидация `initData` по HMAC-SHA256 с проверкой свежести (24 ч)
- **JWT-токены** — выдача при входе, верификация через `JwtAuthenticationFilter`
- **Профили пользователей** — просмотр, включение/выключение email-уведомлений
- **Привязка email к Telegram-аккаунту** (и наоборот) с обработкой конфликтов
- **Объединение аккаунтов** — стратегии KEEP_WEB, KEEP_TELEGRAM, MERGE

---

## REST API

### Аутентификация (`/api/auth/**`)

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/auth/register` | Регистрация по email + отправка OTP |
| `POST` | `/api/auth/verify-email` | Подтверждение OTP, получение JWT |
| `POST` | `/api/auth/resend-code` | Повторная отправка OTP |
| `POST` | `/api/auth/login` | Вход по email + пароль |
| `POST` | `/api/auth/telegram` | Вход/регистрация через Telegram initData |

### Профиль (`/api/profile/**`) — требует JWT

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/profile` | Текущий профиль + статистика алертов |
| `PATCH` | `/api/profile/notifications` | Включить/выключить email-уведомления |
| `POST` | `/api/profile/add-email` | Добавить email к Telegram-аккаунту |
| `POST` | `/api/profile/verify-add-email` | Подтвердить добавление email (OTP) |
| `POST` | `/api/profile/link-telegram` | Привязать Telegram к email-аккаунту |
| `POST` | `/api/profile/resolve-conflict` | Разрешить конфликт при объединении аккаунтов |

---

## Структура проекта

```
src/main/java/ru/tuganov/
├── AuthServiceApplication.java
├── config/
│   └── SecurityConfig.java             — публичные /api/auth/**, остальное по JWT
├── controller/
│   ├── AuthController.java
│   └── ProfileController.java
├── dto/
│   ├── auth/                           — RegisterRequest, LoginRequest, TelegramAuthRequest,
│   │                                     VerifyEmailRequest, ResendCodeRequest, AuthResponse
│   ├── AddEmailRequest.java
│   ├── AddEmailResponse.java           — action: VERIFY | LINK
│   ├── AlertStats.java                 — статистика алертов в профиле
│   ├── LinkTelegramRequest.java
│   ├── ProfileResponse.java
│   ├── ResolveConflictRequest.java     — KEEP_WEB | KEEP_TELEGRAM | MERGE
│   ├── UpdateNotificationsRequest.java
│   └── VerifyAddEmailRequest.java
├── entity/
│   ├── AppUser.java
│   ├── EmailVerificationCode.java
│   └── TrackedInstrument.java          — только для подсчёта alertStats (FK-ссылка)
├── exception/
│   ├── GlobalExceptionHandler.java     — RFC 7807
│   ├── LinkConflictException.java
│   └── ResourceNotFoundException.java
├── repository/
│   ├── AppUserRepository.java
│   ├── EmailVerificationCodeRepository.java
│   └── TrackedInstrumentRepository.java
├── security/
│   ├── AppUserDetails.java
│   ├── AppUserDetailsService.java
│   ├── JwtAuthenticationFilter.java
│   ├── JwtTokenService.java
│   └── TelegramInitDataValidator.java  — HMAC-SHA256, constant-time сравнение
└── service/
    ├── AccountLinkingService.java      — логика объединения аккаунтов
    ├── AppUserService.java             — регистрация, вход, OTP
    ├── EmailService.java               — отправка писем (Spring Mail)
    └── ProfileService.java             — профиль, уведомления
```

---

## Миграции Flyway

| Файл | Содержание |
|------|------------|
| `V1__create_app_users.sql` | Таблица `app_users` (TIMESTAMPTZ, без `telegram_linked`) |
| `V2__create_email_verification.sql` | Таблица `email_verification_codes` (attempts, TIMESTAMPTZ) |

---

## Настройки

```yaml
# application.yml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/market_service
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  mail:
    host: smtp.yandex.ru
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}

jwt:
  secret: ${JWT_SECRET}
  expiration-ms: 86400000   # 24 ч

telegram:
  bot:
    token: ${TELEGRAM_BOT_TOKEN}

app:
  frontend-url: ${FRONTEND_URL:http://localhost:3000}
```

| Переменная | Описание |
|-----------|----------|
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP (Yandex Mail) |
| `JWT_SECRET` | Общий секрет (должен совпадать с AlertService) |
| `TELEGRAM_BOT_TOKEN` | Токен бота для валидации initData |
| `FRONTEND_URL` | Для CORS (`allowedOriginPatterns`) |

---

## Запуск

```bash
# Создать .env
cat > .env << EOF
DB_USERNAME=postgres
DB_PASSWORD=postgres
MAIL_USERNAME=your@yandex.ru
MAIL_PASSWORD=your_app_password
JWT_SECRET=your_256_bit_secret
TELEGRAM_BOT_TOKEN=123456:ABC...
FRONTEND_URL=http://localhost:3000
EOF

# Запустить
./gradlew bootRun
```

PostgreSQL должен быть запущен. Flyway создаст таблицы при первом старте.

---

## Безопасность

- OTP-сравнение через `MessageDigest.isEqual()` (защита от timing-атаки)
- Telegram `initData` проверяется через HMAC-SHA256 с токеном бота
- Свежесть `initData` ограничена 24 часами (настраивается)
- OTP: максимум 5 попыток, время жизни 10 минут
- CORS разрешён только для `FRONTEND_URL`
- JWT срок действия: 24 часа
