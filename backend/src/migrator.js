import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const DEFAULT_MIGRATIONS_DIR = path.resolve(__dirname, '..', 'migrations');

export class MigrationError extends Error {
  constructor(code, message, details = {}) {
    super(message);
    this.name = 'MigrationError';
    this.code = code;
    this.details = details;
  }
}

export function loadMigrations(migrationsDir = DEFAULT_MIGRATIONS_DIR) {
  if (!fs.existsSync(migrationsDir)) {
    throw new MigrationError('MIGRATIONS_DIR_NOT_FOUND', `Migrations directory not found: ${migrationsDir}`);
  }
  const files = fs
    .readdirSync(migrationsDir, { withFileTypes: true })
    .filter((d) => d.isFile() && d.name.endsWith('.sql'))
    .map((d) => d.name)
    .sort();

  const seenVersions = new Set();
  return files.map((filename) => {
    const match = filename.match(/^(\d+)_([a-zA-Z0-9_]+)\.sql$/);
    if (!match) {
      throw new MigrationError(
        'INVALID_MIGRATION_FILENAME',
        `Migration filename must follow NNN_description.sql: ${filename}`
      );
    }
    const version = match[1];
    if (seenVersions.has(version)) {
      throw new MigrationError('DUPLICATE_MIGRATION_VERSION', `Duplicate migration version: ${version}`);
    }
    seenVersions.add(version);

    const fullPath = path.join(migrationsDir, filename);
    const sql = fs.readFileSync(fullPath, 'utf8');
    const checksum = crypto.createHash('sha256').update(sql, 'utf8').digest('hex');
    return {
      version,
      filename,
      checksum,
      sql
    };
  });
}

async function ensureMigrationsTable(client) {
  await client.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      version TEXT PRIMARY KEY,
      filename TEXT NOT NULL,
      checksum TEXT NOT NULL,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
}

export async function getMigrationStatus(pool, { migrationsDir = DEFAULT_MIGRATIONS_DIR } = {}) {
  const migrations = loadMigrations(migrationsDir);
  const client = await pool.connect();
  try {
    await ensureMigrationsTable(client);
    const res = await client.query(
      `SELECT version, filename, checksum, applied_at FROM schema_migrations ORDER BY version ASC`
    );
    const appliedMap = new Map(res.rows.map((r) => [String(r.version), r]));
    const applied = [];
    const pending = [];
    for (const m of migrations) {
      const existing = appliedMap.get(m.version);
      if (existing) {
        if (existing.checksum !== m.checksum) {
          throw new MigrationError(
            'MIGRATION_CHECKSUM_MISMATCH',
            `Applied migration ${m.filename} (version ${m.version}) was modified after application`,
            { version: m.version, expectedChecksum: existing.checksum, actualChecksum: m.checksum }
          );
        }
        applied.push({
          version: m.version,
          filename: m.filename,
          checksum: m.checksum,
          appliedAt: existing.applied_at || null
        });
      } else {
        pending.push({
          version: m.version,
          filename: m.filename,
          checksum: m.checksum
        });
      }
    }
    return { ok: true, total: migrations.length, applied, pending };
  } finally {
    client.release();
  }
}

export async function runMigrations(pool, { migrationsDir = DEFAULT_MIGRATIONS_DIR } = {}) {
  const migrations = loadMigrations(migrationsDir);
  const client = await pool.connect();
  try {
    await ensureMigrationsTable(client);
    const existingRes = await client.query(
      `SELECT version, filename, checksum FROM schema_migrations ORDER BY version ASC`
    );
    const appliedMap = new Map(existingRes.rows.map((r) => [String(r.version), r]));

    const appliedNow = [];
    const skipped = [];

    for (const migration of migrations) {
      const already = appliedMap.get(migration.version);
      if (already) {
        if (already.checksum !== migration.checksum) {
          throw new MigrationError(
            'MIGRATION_CHECKSUM_MISMATCH',
            `Applied migration ${migration.filename} (version ${migration.version}) checksum mismatch`,
            {
              version: migration.version,
              expectedChecksum: already.checksum,
              actualChecksum: migration.checksum
            }
          );
        }
        skipped.push({
          version: migration.version,
          filename: migration.filename,
          checksum: migration.checksum
        });
        continue;
      }

      await client.query('BEGIN');
      try {
        await client.query(migration.sql);
        await client.query(
          `INSERT INTO schema_migrations (version, filename, checksum) VALUES ($1, $2, $3)`,
          [migration.version, migration.filename, migration.checksum]
        );
        await client.query('COMMIT');
        appliedNow.push({
          version: migration.version,
          filename: migration.filename,
          checksum: migration.checksum
        });
      } catch (err) {
        await client.query('ROLLBACK').catch(() => {});
        throw err;
      }
    }

    return {
      ok: true,
      total: migrations.length,
      applied: appliedNow,
      skipped
    };
  } finally {
    client.release();
  }
}
