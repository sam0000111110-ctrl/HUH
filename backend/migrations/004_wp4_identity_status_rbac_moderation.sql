-- Migration 004: WP-4 Identity, +18 Age Gate Lock, Account Status (Ban/Suspend), 4-Role RBAC, and Moderation Samples

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
  ADD COLUMN IF NOT EXISTS status_reason TEXT,
  ADD COLUMN IF NOT EXISTS status_updated_at BIGINT,
  ADD COLUMN IF NOT EXISTS status_updated_by TEXT,
  ADD COLUMN IF NOT EXISTS admin_role TEXT NOT NULL DEFAULT 'NONE' CHECK (admin_role IN ('NONE', 'SUPER_ADMIN', 'FINANCE_ADMIN', 'MODERATION_ADMIN', 'AGENCY_MANAGER')),
  ADD COLUMN IF NOT EXISTS underage_locked BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS underage_locked_reason TEXT,
  ADD COLUMN IF NOT EXISTS underage_locked_at BIGINT;

UPDATE users
SET admin_role = 'SUPER_ADMIN'
WHERE is_admin = TRUE AND (admin_role IS NULL OR admin_role = 'NONE');

CREATE TABLE IF NOT EXISTS underage_device_locks (
    device_hash TEXT PRIMARY KEY,
    uid TEXT,
    dob_submitted TEXT,
    reason TEXT NOT NULL DEFAULT 'UNDERAGE_SUBMISSION',
    locked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS moderation_samples (
    id TEXT PRIMARY KEY,
    stream_id TEXT,
    user_uid TEXT NOT NULL,
    sample_type TEXT NOT NULL DEFAULT 'STREAM_FRAME',
    provider TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'CLEAN',
    risk_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    labels_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    estimated_cost_usd DOUBLE PRECISION NOT NULL DEFAULT 0,
    action_taken TEXT,
    reviewer_uid TEXT,
    details TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_moderation_samples_status_created ON moderation_samples (status, created_at DESC);

CREATE TABLE IF NOT EXISTS moderation_daily_usage (
    day_key TEXT PRIMARY KEY,
    sample_count INT NOT NULL DEFAULT 0 CHECK (sample_count >= 0),
    estimated_cost_usd DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (estimated_cost_usd >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
