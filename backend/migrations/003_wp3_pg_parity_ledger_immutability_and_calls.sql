-- Migration 003: WP-3 PostgreSQL parity, financial ON DELETE RESTRICT, append-only ledger trigger, and call_completions

-- 1. C1 parity: users.agency_name column
ALTER TABLE users ADD COLUMN IF NOT EXISTS agency_name TEXT NOT NULL DEFAULT '';

-- 2. C6 financial safety: replace any ON DELETE CASCADE foreign keys on financial tables with ON DELETE RESTRICT
DO $$
DECLARE
  tbl TEXT;
  con_name TEXT;
BEGIN
  FOREACH tbl IN ARRAY ARRAY['wallet_accounts', 'ledger_holds', 'ledger_entries', 'iap_transactions', 'payout_requests']
  LOOP
    SELECT c.conname INTO con_name
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_class rt ON c.confrelid = rt.oid
    WHERE t.relname = tbl
      AND rt.relname = 'users'
      AND c.contype = 'f'
    LIMIT 1;

    IF con_name IS NOT NULL THEN
      EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', tbl, con_name);
    END IF;

    EXECUTE format(
      'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (uid) REFERENCES users(uid) ON DELETE RESTRICT',
      tbl,
      tbl || '_uid_fkey'
    );
  END LOOP;
END $$;

-- 3. C6 append-only enforcement on ledger_entries: forbid UPDATE and DELETE at database level
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

-- 4. C2 call completions persistence & indexes for call_sessions / call_alerts
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
CREATE INDEX IF NOT EXISTS idx_call_sessions_status ON call_sessions (status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_call_alerts_created ON call_alerts (created_at DESC);
