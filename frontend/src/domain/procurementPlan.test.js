import { describe, expect, it } from 'vitest';
import { filterLines, fyLabel, nextFyStart, plainAmount, quarterRange, toCsv } from './procurementPlan.js';

describe('procurement plan helpers', () => {
  it('labels financial years and quarters', () => {
    expect(fyLabel('2026-07-01')).toBe('2026/27');
    expect(fyLabel('2099-07-01')).toBe('2099/00');
    expect(fyLabel('2027-01-01')).toBe('2027');
    expect(nextFyStart(new Date(2026, 9, 11))).toBe('2027-07-01');
    expect(nextFyStart(new Date(2026, 2, 1))).toBe('2026-07-01');
    expect(quarterRange('2026-07-01', 3)).toBe('Jan 2027 – Mar 2027');
  });

  it('filters lines and writes CSV', () => {
    const lines = [
      { reference: 'APP 2026/27-001', description: 'Transformers', departmentId: 'a', quarter: 2, category: 'GOODS', stage: 'NOT_STARTED', behind: true, cancelled: false },
      { reference: 'APP 2026/27-002', description: 'Line works', departmentId: 'b', quarter: 1, category: 'WORKS', stage: 'AWARDED', behind: false, cancelled: false, budgetCode: 'D-2120' },
      { reference: 'APP 2026/27-003', description: 'Old item', departmentId: 'a', quarter: 1, category: 'GOODS', stage: 'NOT_STARTED', behind: false, cancelled: true },
    ];
    expect(filterLines(lines).map((l) => l.description)).toEqual(['Transformers', 'Line works']);
    expect(filterLines(lines, { departmentId: 'a', showDropped: true })).toHaveLength(2);
    expect(filterLines(lines, { q: 'd-2120' })[0].description).toBe('Line works');
    expect(filterLines(lines, { onlyBehind: true })).toHaveLength(1);
    expect(filterLines(lines, { quarter: '1' })).toHaveLength(1);
    expect(plainAmount(123456)).toBe('1234.56');
    expect(plainAmount(null)).toBe('');
    expect(toCsv([['a', 'b, c', 'say "hi"']])).toBe('a,"b, c","say ""hi"""\r\n');
  });
});
