// Amounts travel as integer minor units (cents) to avoid floating-point errors.

export function formatMoney(minor, currency = 'KES', { compact = false } = {}) {
  if (minor === null || minor === undefined) return '';
  const major = Number(minor) / 100;
  return new Intl.NumberFormat('en-KE', {
    style: 'currency',
    currency,
    notation: compact ? 'compact' : 'standard',
    maximumFractionDigits: compact ? 1 : 2,
  }).format(major);
}

/** Parses a user-typed amount such as "1,250,000.50" into minor units. */
export function toMinorUnits(text) {
  const cleaned = String(text ?? '').replace(/[,\s]/g, '');
  if (cleaned === '') return 0;
  if (!/^\d+(\.\d{0,2})?$/.test(cleaned)) return NaN;
  const [whole, frac = ''] = cleaned.split('.');
  return Number(whole) * 100 + Number(frac.padEnd(2, '0'));
}
