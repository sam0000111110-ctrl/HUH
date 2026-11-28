-- Migration 001: Initial core schema for HUH / Ayer backend
-- Safe & idempotent for existing databases via IF NOT EXISTS.

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
    host_earnings_coins BIGINT NOT NULL DEFAULT 0 CHECK (host_earnings_coins >= 0),
    sub_agents_earnings_coins BIGINT NOT NULL DEFAULT 0 CHECK (sub_agents_earnings_coins >= 0),
    reserved_payout_coins BIGINT NOT NULL DEFAULT 0 CHECK (reserved_payout_coins >= 0),
    total_accumulated_points BIGINT NOT NULL DEFAULT 0 CHECK (total_accumulated_points >= 0),
    total_points_spent BIGINT NOT NULL DEFAULT 0 CHECK (total_points_spent >= 0),
    last_daily_claim_ms BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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

CREATE TABLE IF NOT EXISTS call_alerts (
    alert_id TEXT PRIMARY KEY,
    dedup_key TEXT,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_uid TEXT NOT NULL,
    action TEXT NOT NULL,
    target_id TEXT,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
