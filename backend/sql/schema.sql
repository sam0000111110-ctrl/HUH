-- Authoritative PostgreSQL Schema for HUH / Ayer Production Backend
-- Synchronized with migrations 001..004 (WP-0, WP-2, WP-1, WP-3, WP-4).
-- Financial tables strictly enforce ON DELETE RESTRICT and ledger_entries is append-only via trigger.

CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    filename TEXT NOT NULL,
    checksum TEXT NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
    uid TEXT PRIMARY KEY,
    display_name TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL DEFAULT '',
    photo_url TEXT,
    bio TEXT NOT NULL DEFAULT '',
    country TEXT NOT NULL DEFAULT '',
    country_code TEXT NOT NULL DEFAULT '',
    gender TEXT NOT NULL DEFAULT '',
    dob TEXT,
    age_years INT NOT NULL DEFAULT 0,
    age_verified BOOLEAN NOT NULL DEFAULT FALSE,
    age_verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED',
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    admin_role TEXT NOT NULL DEFAULT 'NONE' CHECK (admin_role IN ('NONE', 'SUPER_ADMIN', 'FINANCE_ADMIN', 'MODERATION_ADMIN', 'AGENCY_MANAGER')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED')),
    status_reason TEXT,
    status_updated_at BIGINT,
    status_updated_by TEXT,
    underage_locked BOOLEAN NOT NULL DEFAULT FALSE,
    underage_locked_reason TEXT,
    underage_locked_at BIGINT,
    is_agent BOOLEAN NOT NULL DEFAULT FALSE,
    is_host BOOLEAN NOT NULL DEFAULT FALSE,
    host_status TEXT NOT NULL DEFAULT 'NONE',
    agent_commission_percent INT NOT NULL DEFAULT 20,
    host_commission_percent INT NOT NULL DEFAULT 50,
    agency_id TEXT NOT NULL DEFAULT '',
    agency_name TEXT NOT NULL DEFAULT '',
    referral_code TEXT NOT NULL DEFAULT '',
    profile_visibility TEXT NOT NULL DEFAULT 'PUBLIC',
    direct_message_permission TEXT NOT NULL DEFAULT 'EVERYONE',
    accepted_terms_at BIGINT,
    accepted_privacy_at BIGINT,
    deleted_at BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wallet_accounts (
    uid TEXT PRIMARY KEY REFERENCES users(uid) ON DELETE RESTRICT,
    points_balance BIGINT NOT NULL DEFAULT 0 CHECK (points_balance >= 0),
    debt_coins BIGINT NOT NULL DEFAULT 0 CHECK (debt_coins >= 0),
    held_coins BIGINT NOT NULL DEFAULT 0 CHECK (held_coins >= 0),
    host_earnings_coins BIGINT NOT NULL DEFAULT 0 CHECK (host_earnings_coins >= 0),
    sub_agents_earnings_coins BIGINT NOT NULL DEFAULT 0 CHECK (sub_agents_earnings_coins >= 0),
    reserved_payout_coins BIGINT NOT NULL DEFAULT 0 CHECK (reserved_payout_coins >= 0),
    platform_revenue_coins BIGINT NOT NULL DEFAULT 0 CHECK (platform_revenue_coins >= 0),
    total_accumulated_points BIGINT NOT NULL DEFAULT 0 CHECK (total_accumulated_points >= 0),
    total_points_spent BIGINT NOT NULL DEFAULT 0 CHECK (total_points_spent >= 0),
    last_daily_claim_ms BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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

CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    entry_uuid TEXT UNIQUE NOT NULL,
    uid TEXT NOT NULL REFERENCES users(uid) ON DELETE RESTRICT,
    entry_type TEXT NOT NULL,
    bucket TEXT NOT NULL DEFAULT 'POINTS',
    delta BIGINT NOT NULL,
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    idempotency_key TEXT UNIQUE NOT NULL,
    reference_id TEXT,
    description TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_uid_created ON ledger_entries (uid, created_at DESC);

CREATE OR REPLACE FUNCTION prevent_ledger_entries_mutation()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'ledger_entries is append-only: % is forbidden', TG_OP
    USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ledger_entries_immutable ON ledger_entries;
CREATE TRIGGER trg_ledger_entries_immutable
  BEFORE UPDATE OR DELETE ON ledger_entries
  FOR EACH ROW
  EXECUTE FUNCTION prevent_ledger_entries_mutation();

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

CREATE TABLE IF NOT EXISTS iap_transactions (
    id BIGSERIAL PRIMARY KEY,
    purchase_token TEXT UNIQUE NOT NULL,
    order_id TEXT,
    uid TEXT NOT NULL REFERENCES users(uid) ON DELETE RESTRICT,
    package_id TEXT NOT NULL,
    product_id TEXT NOT NULL,
    coins_credited BIGINT NOT NULL CHECK (coins_credited > 0),
    amount_micros BIGINT NOT NULL DEFAULT 0,
    currency_code TEXT NOT NULL DEFAULT 'USD',
    purchase_state INT NOT NULL,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    obfuscated_external_account_id TEXT,
    is_voided BOOLEAN NOT NULL DEFAULT FALSE,
    voided_at TIMESTAMPTZ,
    voided_reason TEXT,
    verification_source TEXT NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agencies (
    id TEXT PRIMARY KEY,
    owner_uid TEXT NOT NULL,
    name TEXT NOT NULL,
    country TEXT NOT NULL DEFAULT '',
    commission_percent INT NOT NULL DEFAULT 20 CHECK (commission_percent BETWEEN 1 AND 70),
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS agency_memberships (
    uid TEXT PRIMARY KEY REFERENCES users(uid) ON DELETE CASCADE,
    agency_id TEXT NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'SUB_AGENT',
    parent_agent_uid TEXT,
    commission_percent INT NOT NULL DEFAULT 20,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payout_requests (
    id TEXT PRIMARY KEY,
    uid TEXT NOT NULL REFERENCES users(uid) ON DELETE RESTRICT,
    amount_coins BIGINT NOT NULL CHECK (amount_coins > 0),
    usd_equivalent_cents BIGINT NOT NULL CHECK (usd_equivalent_cents > 0),
    payout_method TEXT NOT NULL,
    bank_account_holder TEXT NOT NULL,
    bank_name TEXT NOT NULL,
    iban_or_account TEXT NOT NULL,
    iban_masked TEXT NOT NULL DEFAULT '',
    from_sub_agents_coins BIGINT NOT NULL DEFAULT 0 CHECK (from_sub_agents_coins >= 0),
    from_host_coins BIGINT NOT NULL DEFAULT 0 CHECK (from_host_coins >= 0),
    earliest_review_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours'),
    status TEXT NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewer_uid TEXT,
    reviewer_note TEXT,
    payment_reference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payout_requests_status ON payout_requests (status, created_at DESC);

CREATE TABLE IF NOT EXISTS age_verifications (
    uid TEXT PRIMARY KEY REFERENCES users(uid) ON DELETE CASCADE,
    display_name TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL DEFAULT '',
    dob TEXT NOT NULL,
    age_years INT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewer_uid TEXT,
    rejection_reason TEXT,
    accepted_terms_at BIGINT NOT NULL,
    accepted_privacy_at BIGINT NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS call_governance_config (
    id INT PRIMARY KEY DEFAULT 1,
    config_json JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS call_sessions (
    call_id TEXT PRIMARY KEY,
    room_name TEXT NOT NULL,
    caller_id TEXT NOT NULL,
    callee_id TEXT NOT NULL,
    call_type TEXT NOT NULL,
    status TEXT NOT NULL,
    payload_json JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_sessions_status ON call_sessions (status, updated_at DESC);

CREATE TABLE IF NOT EXISTS call_alerts (
    alert_id TEXT PRIMARY KEY,
    dedup_key TEXT,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_alerts_created ON call_alerts (created_at DESC);

CREATE TABLE IF NOT EXISTS call_completions (
    call_id TEXT PRIMARY KEY,
    status TEXT NOT NULL,
    call_type TEXT NOT NULL,
    caller_id TEXT NOT NULL,
    callee_id TEXT NOT NULL,
    payload_json JSONB NOT NULL,
    logged_at BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_completions_logged_at ON call_completions (logged_at DESC);

CREATE TABLE IF NOT EXISTS moderation_reports (
    id TEXT PRIMARY KEY,
    report_type TEXT NOT NULL,
    target_user_id TEXT,
    stream_id TEXT,
    reporter_uid TEXT NOT NULL,
    reason TEXT NOT NULL,
    details TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING_REVIEW',
    action_taken TEXT,
    reviewer_uid TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_uid TEXT NOT NULL,
    blocked_uid TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_uid, blocked_uid)
);

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

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_uid TEXT NOT NULL,
    action TEXT NOT NULL,
    target_id TEXT,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
