import { describe, expect, it } from 'vitest';
import { csvRows, defaultParams, formatCell, isNumeric, reportQuery } from './reports.js';

describe('report helpers', () => {
  it('formats cells by column type', () => {
    expect(formatCell('PERCENT', 42.5)).toBe('42.5%');
    expect(formatCell('INTEGER', 1200)).toBe('1,200');
    expect(formatCell('NUMBER', 9.1)).toBe('9.1');
    expect(formatCell('DATE', '2026-10-11')).toBe('11 Oct 2026');
    expect(formatCell('TEXT', null)).toBe('');
    expect(isNumeric('MONEY')).toBe(true);
    expect(isNumeric('DATE')).toBe(false);
  });

  it('builds CSV rows with totals and default filters', () => {
    const result = {
      columns: [
        { key: 'name', label: 'Name', type: 'TEXT' },
        { key: 'amount', label: 'Amount', type: 'MONEY' },
      ],
      rows: [{ name: 'A', amount: 12345 }],
      totals: { amount: 12345 },
    };
    expect(csvRows(result)).toEqual([
      ['Name', 'Amount'],
      ['A', '123.45'],
      ['Total', '123.45'],
    ]);
    expect(defaultParams([{ key: 'status', defaultValue: 'OPEN' }, { key: 'p', defaultValue: null }])).toEqual({ status: 'OPEN', p: '' });
  });
});

describe('report query', () => {
  it('keeps blank filters, which mean "all"', () => {
    expect(reportQuery({ status: '', projectId: 'p1', x: null })).toBe('?status=&projectId=p1');
    expect(reportQuery({})).toBe('');
  });
});
