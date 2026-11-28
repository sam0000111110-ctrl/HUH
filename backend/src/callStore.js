import fs from 'node:fs';
import path from 'node:path';
import { runMigrations } from './migrator.js';

function parseJsonCol(val) {
  if (val === null || val === undefined) return null;
  if (typeof val === 'string') {
    try {
      return JSON.parse(val);
    } catch {
      return null;
    }
  }
  return val;
}

export class MemoryCallStore {
  constructor({ dataFile = '' } = {}) {
    this.dataFile = dataFile ? String(dataFile) : '';
    this.mode = this.dataFile ? 'json-file' : 'memory';
  }

  async init() {
    return { ok: true, mode: this.mode };
  }

  async loadState() {
    if (!this.dataFile) return null;
    try {
      if (fs.existsSync(this.dataFile)) {
        const parsed = JSON.parse(fs.readFileSync(this.dataFile, 'utf8'));
        return parsed && typeof parsed === 'object' ? parsed : null;
      }
    } catch {
      return null;
    }
    return null;
  }

  persistSnapshot(engine) {
    if (!this.dataFile || !engine) return;
    try {
      const resolved = path.resolve(this.dataFile);
      const dir = path.dirname(resolved);
      fs.mkdirSync(dir, { recursive: true });
      const tmpFile = `${resolved}.${process.pid}.tmp`;
      fs.writeFileSync(tmpFile, JSON.stringify(engine.exportState(), null, 2), 'utf8');
      fs.renameSync(tmpFile, resolved);
    } catch {
      // non-fatal disk flush
    }
  }

  async saveConfig(_config, engine = null) {
    this.persistSnapshot(engine);
  }

  async upsertCall(_call, engine = null) {
    this.persistSnapshot(engine);
  }

  async appendCallLog(_entry, engine = null) {
    this.persistSnapshot(engine);
  }

  async upsertAlert(_alert, engine = null) {
    this.persistSnapshot(engine);
  }
}

export class PostgresCallStore {
  constructor({ pool, runSchemaMigrations = true } = {}) {
    if (!pool) {
      throw new Error('PostgresCallStore requires a pg Pool');
    }
    this.mode = 'postgres';
    this.pool = pool;
    this.runSchemaMigrations = runSchemaMigrations;
    this._initPromise = null;
  }

  async init() {
    if (!this._initPromise) {
      this._initPromise = (async () => {
        if (this.runSchemaMigrations) {
          await runMigrations(this.pool);
        }
        return { ok: true, mode: this.mode };
      })();
    }
    return this._initPromise;
  }

  async loadState() {
    await this.init();
    const [cfgRes, sessRes, alertRes, logRes] = await Promise.all([
      this.pool.query(`SELECT config_json FROM call_governance_config WHERE id = 1`),
      this.pool.query(`SELECT payload_json FROM call_sessions ORDER BY updated_at DESC LIMIT 2000`),
      this.pool.query(`SELECT payload_json FROM call_alerts ORDER BY created_at DESC LIMIT 200`),
      this.pool.query(`SELECT payload_json FROM call_completions ORDER BY logged_at DESC LIMIT 2000`)
    ]);

    const config = cfgRes.rowCount > 0 ? parseJsonCol(cfgRes.rows[0].config_json) : null;
    const calls = sessRes.rows.map((r) => parseJsonCol(r.payload_json)).filter(Boolean);
    const alerts = alertRes.rows.map((r) => parseJsonCol(r.payload_json)).filter(Boolean);
    const callLogs = logRes.rows.map((r) => parseJsonCol(r.payload_json)).filter(Boolean);

    return { config, calls, alerts, callLogs };
  }

  async saveConfig(config) {
    await this.init();
    await this.pool.query(
      `INSERT INTO call_governance_config (id, config_json, updated_at)
       VALUES (1, $1::jsonb, NOW())
       ON CONFLICT (id) DO UPDATE SET
         config_json = EXCLUDED.config_json,
         updated_at = NOW()`,
      [JSON.stringify(config)]
    );
  }

  async upsertCall(call) {
    if (!call?.callId) return;
    await this.init();
    await this.pool.query(
      `INSERT INTO call_sessions (call_id, room_name, caller_id, callee_id, call_type, status, payload_json, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb, NOW())
       ON CONFLICT (call_id) DO UPDATE SET
         status = EXCLUDED.status,
         payload_json = EXCLUDED.payload_json,
         updated_at = NOW()`,
      [
        call.callId,
        call.roomName,
        call.callerId,
        call.calleeId,
        call.callType,
        call.status,
        JSON.stringify(call)
      ]
    );
  }

  async appendCallLog(entry) {
    if (!entry?.callId) return;
    await this.init();
    await this.pool.query(
      `INSERT INTO call_completions (call_id, status, call_type, caller_id, callee_id, payload_json, logged_at)
       VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7)
       ON CONFLICT (call_id) DO NOTHING`,
      [
        entry.callId,
        entry.status,
        entry.callType,
        entry.callerId,
        entry.calleeId,
        JSON.stringify(entry),
        Number(entry.loggedAt || Date.now())
      ]
    );
  }

  async upsertAlert(alert) {
    if (!alert?.id) return;
    await this.init();
    await this.pool.query(
      `INSERT INTO call_alerts (alert_id, dedup_key, payload_json)
       VALUES ($1, $2, $3::jsonb)
       ON CONFLICT (alert_id) DO UPDATE SET payload_json = EXCLUDED.payload_json`,
      [alert.id, alert.dedupKey || null, JSON.stringify(alert)]
    );
  }
}
