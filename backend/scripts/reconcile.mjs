import { createLedgerStore } from '../src/ledgerStore.js';
import { reconcileLedgerSnapshot } from '../src/reconcileLedger.js';

async function main() {
  const store = await createLedgerStore(process.env);
  const snapshot = await store.exportReconciliationSnapshot();
  const report = reconcileLedgerSnapshot(snapshot);
  console.log(JSON.stringify({ mode: store.mode, ...report }, null, 2));
  if (!report.ok) {
    process.exitCode = 1;
  }
  if (store.pool && typeof store.pool.end === 'function') {
    await store.pool.end();
  }
}

main().catch((err) => {
  console.error('[reconcile] Failed:', err.message);
  process.exit(1);
});
