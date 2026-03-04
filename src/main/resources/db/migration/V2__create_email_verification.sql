-- ============================================================
-- V2: Таблица кодов подтверждения email (OTP, TTL 15 мин)
-- ============================================================

CREATE TABLE IF NOT EXISTS email_verification_codes (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    code       VARCHAR(6)  NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used       BOOLEAN     NOT NULL DEFAULT FALSE,
    attempts   INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evc_user_id    ON email_verification_codes(user_id);
CREATE INDEX IF NOT EXISTS idx_evc_expires_at ON email_verification_codes(expires_at);
