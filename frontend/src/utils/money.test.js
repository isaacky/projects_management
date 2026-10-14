import { describe, expect, it } from 'vitest';
import { formatMoney, toMinorUnits } from './money.js';

describe('toMinorUnits', () => {
  it('parses whole and decimal amounts with separators', () => {
    expect(toMinorUnits('1,250,000')).toBe(125_000_000);
    expect(toMinorUnits('10.5')).toBe(1050);
    expect(toMinorUnits('10.05')).toBe(1005);
    expect(toMinorUnits('')).toBe(0);
  });

  it('rejects invalid input', () => {
    expect(toMinorUnits('12.345')).toBeNaN();
    expect(toMinorUnits('abc')).toBeNaN();
    expect(toMinorUnits('-5')).toBeNaN();
  });
});

describe('formatMoney', () => {
  it('formats minor units as currency', () => {
    expect(formatMoney(125_000)).toContain('1,250');
    expect(formatMoney(null)).toBe('');
  });
});
