import { loadMigrations, runMigrations, getMigrationStatus } from '../src/migrator.js';

const args = new Set(process.argv.slice(2));
const isPlanOnly = args.has('--plan') || args.has('--dry-run');
const isStatusOnly = args.has('--status');

async function main() {
  if (isPlanOnly && !process.env.DATABASE_URL) {
    const migrations = loadMigrations();
    console.log(
      JSON.stringify(
        {
          ok: true,
          mode: 'plan-offline',
          migrations: migrations.map((m) => ({
            version: m.version,
            filename: m.filename,
            checksum: m.checksum
          }))
        },
        null,
        2
      )
    );
    return;
  }

  if (!process.env.DATABASE_URL) {
    console.error('DATABASE_URL is required to execute PostgreSQL migrations (pass --plan to inspect migration files).');
    process.exitCode = 1;
    return;
  }

  const { Pool } = await import('pg');
  const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: process.env.PG_SSL === 'true' ? { rejectUnauthorized: false } : undefined
  });

  try {
    const result = isStatusOnly
      ? await getMigrationStatus(pool)
      : await runMigrations(pool);
    console.log(JSON.stringify(result, null, 2));
  } finally {
    await pool.end();
  }
}

main().catch((err) => {
  console.error('Migration failed:', err?.message || err);
  process.exitCode = 1;
});
