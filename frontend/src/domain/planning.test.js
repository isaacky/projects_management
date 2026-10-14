import { describe, expect, it } from 'vitest';
import { addDays, daysBetween, formatPredecessors, parentIndexes, parsePredecessors, riskLevel } from './planning.js';

describe('planning helpers', () => {
  it('reads and writes predecessor text', () => {
    expect(parsePredecessors('3, 5SS+2; 7fs-1')).toEqual([
      { row: 3, type: 'FS', lagDays: 0 },
      { row: 5, type: 'SS', lagDays: 2 },
      { row: 7, type: 'FS', lagDays: -1 },
    ]);
    expect(parsePredecessors('')).toEqual([]);
    expect(() => parsePredecessors('3FF')).toThrow();
    expect(formatPredecessors([{ row: 3, type: 'FS', lagDays: 0 }, { row: 5, type: 'SS', lagDays: 2 }])).toBe('3, 5SS+2');
    expect(formatPredecessors([{ row: 7, type: 'FS', lagDays: -1 }])).toBe('7FS-1');
  });

  it('works out parents from indentation', () => {
    const rows = [{ level: 0 }, { level: 1 }, { level: 1 }, { level: 2 }, { level: 0 }];
    expect(parentIndexes(rows)).toEqual([null, 0, 0, 2, null]);
    expect(() => parentIndexes([{ level: 0 }, { level: 2 }])).toThrow();
  });

  it('counts days and scores risks', () => {
    expect(daysBetween('2026-11-02', '2026-11-12')).toBe(10);
    expect(addDays('2026-12-30', 3)).toBe('2027-01-02');
    expect(riskLevel(4, 5)).toBe('CRITICAL');
    expect(riskLevel(2, 5)).toBe('HIGH');
    expect(riskLevel(1, 4)).toBe('LOW');
  });
});
