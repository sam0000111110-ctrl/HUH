/**
 * Authoritative Ledger Conservation & Invariant Verifier (WP-1 Exit Gate)
 *
 * Verifies:
 * 1. Non-negative balances on every wallet bucket (points, debt, held, host, agent, reserved, platform).
 * 2. Active holds sum === wallet.heldCoins for every user.
 * 3. Active pending/approved payouts sum === wallet.reservedPayoutCoins for every user.
 * 4. Per-user POINTS ledger replay === wallet.pointsBalance.
 * 5. Global coin supply conservation:
 *    sum(points + held + hostEarnings + agentEarnings + reservedPayout + platformRevenue) + paidOutCoins
 *    === mintedPoints (PURCHASE + DAILY_FREE + MISSION_REWARD) - clawbackDeductedPoints
 */
export function reconcileLedgerSnapshot({
  wallets = [],
  ledgerEntries = [],
  holds = [],
  payoutRequests = []
}) {
  const discrepancies = [];

  const nonNegFields = [
    'pointsBalance',
    'debtCoins',
    'heldCoins',
    'hostEarningsCoins',
    'subAgentsEarningsCoins',
    'reservedPayoutCoins',
    'platformRevenueCoins',
    'totalAccumulatedPoints',
    'totalPointsSpent'
  ];

  const activeHoldByUid = new Map();
  for (const h of holds) {
    if (h.status === 'ACTIVE') {
      const rem = Number(h.amountCoins) - Number(h.capturedCoins || 0) - Number(h.releasedCoins || 0);
      if (rem < 0) {
        discrepancies.push(`Negative remaining hold ${h.holdId}: ${rem}`);
      }
      activeHoldByUid.set(h.uid, (activeHoldByUid.get(h.uid) || 0) + rem);
    }
  }

  const activeReservedPayoutByUid = new Map();
  let paidOutCoins = 0;
  for (const p of payoutRequests) {
    const amt = Number(p.amountCoins || 0);
    if (p.status === 'PENDING_REVIEW' || p.status === 'APPROVED') {
      activeReservedPayoutByUid.set(p.uid, (activeReservedPayoutByUid.get(p.uid) || 0) + amt);
    } else if (p.status === 'PAID') {
      paidOutCoins += amt;
    }
  }

  const pointsReplayByUid = new Map();
  let totalMintedCoins = 0;
  let totalClawbackDeducted = 0;

  for (const entry of ledgerEntries) {
    const delta = Number(entry.delta || 0);
    if (entry.bucket === 'POINTS') {
      pointsReplayByUid.set(entry.uid, (pointsReplayByUid.get(entry.uid) || 0) + delta);
    }
    if (entry.entryType === 'PURCHASE' || entry.entryType === 'DAILY_FREE' || entry.entryType === 'MISSION_REWARD') {
      totalMintedCoins += delta;
    } else if (entry.entryType === 'REFUND_CLAWBACK') {
      // delta is <= 0
      totalClawbackDeducted += Math.abs(delta);
    }
  }

  let totalPointsBalance = 0;
  let totalHeldCoins = 0;
  let totalHostEarnings = 0;
  let totalAgentEarnings = 0;
  let totalReservedPayouts = 0;
  let totalPlatformRevenue = 0;

  for (const w of wallets) {
    for (const f of nonNegFields) {
      const val = Number(w[f] || 0);
      if (val < 0 || !Number.isFinite(val)) {
        discrepancies.push(`Wallet ${w.uid} invalid/negative ${f}: ${w[f]}`);
      }
    }

    const expectedHeld = activeHoldByUid.get(w.uid) || 0;
    if (Number(w.heldCoins || 0) !== expectedHeld) {
      discrepancies.push(`Wallet ${w.uid} heldCoins=${w.heldCoins} != activeHoldsSum=${expectedHeld}`);
    }

    const expectedReserved = activeReservedPayoutByUid.get(w.uid) || 0;
    if (Number(w.reservedPayoutCoins || 0) !== expectedReserved) {
      discrepancies.push(`Wallet ${w.uid} reservedPayoutCoins=${w.reservedPayoutCoins} != pendingPayouts=${expectedReserved}`);
    }

    const replayedPoints = pointsReplayByUid.get(w.uid) || 0;
    if (Number(w.pointsBalance || 0) !== replayedPoints) {
      discrepancies.push(`Wallet ${w.uid} pointsBalance=${w.pointsBalance} != ledgerPointsReplay=${replayedPoints}`);
    }

    totalPointsBalance += Number(w.pointsBalance || 0);
    totalHeldCoins += Number(w.heldCoins || 0);
    totalHostEarnings += Number(w.hostEarningsCoins || 0);
    totalAgentEarnings += Number(w.subAgentsEarningsCoins || 0);
    totalReservedPayouts += Number(w.reservedPayoutCoins || 0);
    totalPlatformRevenue += Number(w.platformRevenueCoins || 0);
  }

  const totalAccountedSupply =
    totalPointsBalance +
    totalHeldCoins +
    totalHostEarnings +
    totalAgentEarnings +
    totalReservedPayouts +
    totalPlatformRevenue +
    paidOutCoins;

  const expectedNetMinted = totalMintedCoins - totalClawbackDeducted;
  if (totalAccountedSupply !== expectedNetMinted) {
    discrepancies.push(
      `Global conservation mismatch: accounted=${totalAccountedSupply} != netMinted=${expectedNetMinted}`
    );
  }

  return {
    ok: discrepancies.length === 0,
    discrepancyCount: discrepancies.length,
    discrepancies,
    totals: {
      walletsCount: wallets.length,
      ledgerEntriesCount: ledgerEntries.length,
      holdsCount: holds.length,
      payoutRequestsCount: payoutRequests.length,
      totalPointsBalance,
      totalHeldCoins,
      totalHostEarnings,
      totalAgentEarnings,
      totalReservedPayouts,
      totalPlatformRevenue,
      paidOutCoins,
      totalAccountedSupply,
      totalMintedCoins,
      totalClawbackDeducted,
      expectedNetMinted
    }
  };
}
