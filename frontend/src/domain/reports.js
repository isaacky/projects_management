// Reports: formatting a cell by its column type, and turning a result into CSV rows.
import { formatMoney } from '../utils/money.js';

export function formatCell(type, value, currency = 'KES') {
  if (value === null || value === undefined || value === '') return '';
  switch (type) {
    case 'MONEY':
      return formatMoney(value, currency);
    case 'PERCENT':
      return `${Number(value).toLocaleString('en-KE', { maximumFractionDigits: 1 })}%`;
    case 'INTEGER':
      return Number(value).toLocaleString('en-KE', { maximumFractionDigits: 0 });
    case 'NUMBER':
      return Number(value).toLocaleString('en-KE', { maximumFractionDigits: 4 });
    case 'DATE':
      return new Date(`${value}T00:00:00`).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    default:
      return String(value);
  }
}

/** Numbers line up on the right. */
export function isNumeric(type) {
  return type === 'MONEY' || type === 'PERCENT' || type === 'INTEGER' || type === 'NUMBER';
}

/** Header plus rows as plain values for a CSV file: money in major units, dates as yyyy-mm-dd. */
export function csvRows(result) {
  const plain = (type, v) => {
    if (v === null || v === undefined) return '';
    if (type === 'MONEY') return (Number(v) / 100).toFixed(2);
    return String(v);
  };
  const header = result.columns.map((c) => c.label);
  const rows = result.rows.map((r) => result.columns.map((c) => plain(c.type, r[c.key])));
  const out = [header, ...rows];
  if (result.totals && Object.keys(result.totals).length) {
    out.push(result.columns.map((c, i) => (i === 0 && result.totals[c.key] == null ? 'Total' : plain(c.type, result.totals[c.key]))));
  }
  return out;
}

/** Filter values to start from: each filter's default. */
export function defaultParams(filters) {
  return Object.fromEntries((filters ?? []).map((f) => [f.key, f.defaultValue ?? '']));
}

/**
 * Query string from filter values. Blank values are sent too: a blank filter means "all", which
 * differs from leaving it out (the report's default, e.g. open risks only).
 */
export function reportQuery(params) {
  const qs = new URLSearchParams(Object.entries(params ?? {}).filter(([, v]) => v !== undefined && v !== null));
  const s = qs.toString();
  return s ? `?${s}` : '';
}
