import { describe, expect, it } from 'vitest';
import { barValue, formatPct, formatValue, sparkPoints } from './benefits.js';

describe('benefits helpers', () => {
  it('formats values and progress', () => {
    expect(formatValue(12000, 'customers')).toBe('12,000 customers');
    expect(formatValue(24, '%')).toBe('24%');
    expect(formatValue(9.1, '% of energy')).toBe('9.1% of energy');
    expect(formatValue(null, '%')).toBe('—');
    expect(formatPct(42.42)).toBe('42%');
    expect(barValue(-6)).toBe(0);
    expect(barValue(130)).toBe(100);
  });

  it('scales measurements between baseline and target', () => {
    const b = {
      baselineDate: '2026-01-01',
      targetDate: '2027-01-01',
      baselineValue: 38,
      targetValue: 5,
      measurements: [{ measuredOn: '2026-07-02', value: 24 }],
    };
    const p = sparkPoints(b, 100, 40);
    expect(p.line).toHaveLength(2);
    expect(p.line[0].y).toBe(0); // baseline is the highest value
    expect(p.target.y).toBe(40); // target the lowest
    expect(Math.round(p.line[1].x)).toBe(50);
  });
});
