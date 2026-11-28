-- Migration 002: WP-2 (Billing & Debt Clawback) + WP-1 (Holds, Economy Config, Device Caps, Encrypted Payouts)

CREATE TABLE IF NOT EXISTS ledger_holds (
    hold_id TEXT PRIMARY KEY,
    uid TEXT NOT NULL REFERENCES users(uid) ON DELETE RESTRICT,
    purpose TEXT NOT NULL,
    reference_id TEXT,
    amount_coins BIGINT NOT NULL CHECK (amount_coins > 0),
    captured_coins BIGINT NOT NULL DEFAULT 0 CHECK (captured_coins >= 0),
    released_coins BIGINT NOT NULL DEFAULT 0 CHECK (released_coins >= 0),
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    idempotency_key TEXT UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    settled_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ledger_holds_uid_status ON ledger_holds (uid, status);

CREATE TABLE IF NOT EXISTS economy_config (
    id INT PRIMARY KEY DEFAULT 1,
    host_commission_percent INT NOT NULL DEFAULT 50,
    agent_commission_percent INT NOT NULL DEFAULT 20,
    platform_commission_percent INT NOT NULL DEFAULT 30,
    min_platform_percent INT NOT NULL DEFAULT 15,
    coins_per_usd INT NOT NULL DEFAULT 250,
    min_payout_coins INT NOT NULL DEFAULT 500,
    payout_hold_hours INT NOT NULL DEFAULT 24,
    daily_free_reward_cap_coins INT NOT NULL DEFAULT 25,
    updated_by TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS daily_device_claims (
    device_hash TEXT NOT NULL,
    day_key TEXT NOT NULL,
    last_daily_claim_ms BIGINT NOT NULL DEFAULT 0,
    free_coins_claimed BIGINT NOT NULL DEFAULT 0 CHECK (free_coins_claimed >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (device_hash, day_key)
);

CREATE TABLE IF NOT EXISTS daily_activity_counters (
    uid TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
    day_key TEXT NOT NULL,
    activity_type TEXT NOT NULL,
    activity_count INT NOT NULL DEFAULT 0 CHECK (activity_count >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (uid, day_key, activity_type)
);

ALTER TABLE wallet_accounts ADD COLUMN IF NOT EXISTS debt_coins BIGINT NOT NULL DEFAULT 0 CHECK (debt_coins >= 0);
ALTER TABLE wallet_accounts ADD COLUMN IF NOT EXISTS held_coins BIGINT NOT NULL DEFAULT 0 CHECK (held_coins >= 0);
ALTER TABLE wallet_accounts ADD COLUMN IF NOT EXISTS platform_revenue_coins BIGINT NOT NULL DEFAULT 0 CHECK (platform_revenue_coins >= 0);

ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS iban_masked TEXT NOT NULL DEFAULT '';
ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS from_sub_agents_coins BIGINT NOT NULL DEFAULT 0 CHECK (from_sub_agents_coins >= 0);
ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS from_host_coins BIGINT NOT NULL DEFAULT 0 CHECK (from_host_coins >= 0);
ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS earliest_review_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours');

ALTER TABLE iap_transactions ADD COLUMN IF NOT EXISTS obfuscated_external_account_id TEXT;
ALTER TABLE iap_transactions ADD COLUMN IF NOT EXISTS is_voided BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE iap_transactions ADD COLUMN IF NOT EXISTS voided_at TIMESTAMPTZ;
ALTER TABLE iap_transactions ADD COLUMN IF NOT EXISTS voided_reason TEXT;
