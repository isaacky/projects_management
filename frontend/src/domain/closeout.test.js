import { describe, expect, it } from 'vitest';
import { assetsToRequest, checklistProgress, defectCounts } from './closeout.js';
import { toMinorUnits } from '../utils/money.js';

describe('closeout helpers', () => {
  it('counts checklist progress and defects', () => {
    const p = checklistProgress([{ status: 'DONE' }, { status: 'NOT_APPLICABLE' }, { status: 'OPEN' }, { status: 'OPEN' }]);
    expect(p).toEqual({ done: 1, na: 1, open: 2, total: 4, pct: 50 });
    expect(checklistProgress([]).pct).toBe(0);
    const d = defectCounts([
      { status: 'OPEN', severity: 'CRITICAL', overdue: true },
      { status: 'RECTIFIED', severity: 'MINOR', overdue: false },
      { status: 'CLOSED', severity: 'CRITICAL', overdue: false },
    ]);
    expect(d).toEqual({ outstanding: 2, critical: 1, overdue: 1, toCheck: 1 });
  });

  it('turns asset rows into a request', () => {
    const { assets, errors } = assetsToRequest(
      [
        { description: 'Transformer', quantity: '2', unit: 'each', tag: ' T-1, T-2 ', location: '', value: '1,500,000' },
        { description: 'Cable', quantity: '0', unit: 'm', value: '' },
        { description: 'Poles', quantity: '10', unit: 'each', value: 'abc' },
      ],
      toMinorUnits,
    );
    expect(assets[0]).toEqual({ description: 'Transformer', quantity: 2, unit: 'each', tag: 'T-1, T-2', location: null, valueMinor: 150000000 });
    expect(Object.keys(errors)).toEqual(['1', '2']);
  });
});
