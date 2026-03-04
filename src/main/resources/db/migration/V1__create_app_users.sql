-- ============================================================
-- V1: Создание таблицы пользователей приложения
-- ============================================================

CREATE TABLE IF NOT EXISTS app_users (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email                       VARCHAR(255) UNIQUE,
    password_hash               VARCHAR(255),
    email_confirmed             BOOLEAN     NOT NULL DEFAULT FALSE,
    email_notifications_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    telegram_id                 BIGINT      UNIQUE,
    chat_id                     BIGINT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_users_telegram_id ON app_users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_app_users_email        ON app_users(email);
