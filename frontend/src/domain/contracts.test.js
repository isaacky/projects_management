import { describe, expect, it } from 'vitest';
import { bpToPercent, percentToBp, portfolioTotals, retention, share } from './contracts.js';

describe('contract helpers', () => {
  it('converts between percent and basis points', () => {
    expect(bpToPercent(1000)).toBe('10%');
    expect(bpToPercent(750)).toBe('7.5%');
    expect(percentToBp('10')).toBe(1000);
    expect(percentToBp('2.5')).toBe(250);
    expect(Number.isNaN(percentToBp('abc'))).toBe(true);
  });

  it('computes retention like the server', () => {
    expect(retention(1_000_000, 1000)).toBe(100_000);
    expect(retention(15, 1000)).toBe(2);
    expect(retention(14, 1000)).toBe(1);
  });

  it('adds up live contracts only', () => {
    const t = portfolioTotals([
      { status: 'ACTIVE', currentValueMinor: 100, certifiedMinor: 40, paidMinor: 30 },
      { status: 'DRAFT', currentValueMinor: 999, certifiedMinor: 0, paidMinor: 0 },
      { status: 'COMPLETED', currentValueMinor: 50, certifiedMinor: 50, paidMinor: 50 },
    ]);
    expect(t).toEqual({ committed: 150, certified: 90, paid: 80, count: 2 });
    expect(share(30, 120)).toBe(25);
    expect(share(1, 0)).toBe(0);
  });
});
