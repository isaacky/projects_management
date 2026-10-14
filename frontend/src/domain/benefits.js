// Benefits realisation: labels and small formatting helpers.

export const CATEGORY = {
  FINANCIAL: 'Financial',
  OPERATIONAL: 'Operational',
  CUSTOMER: 'Customer',
  SAFETY: 'Safety',
  ENVIRONMENTAL: 'Environmental',
  STRATEGIC: 'Strategic',
};

export const FREQUENCY = {
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  SEMI_ANNUAL: 'Every 6 months',
  ANNUAL: 'Yearly',
};

export const RATING = {
  ACHIEVED: { label: 'Target reached', color: 'success' },
  ON_TRACK: { label: 'On track', color: 'success' },
  AT_RISK: { label: 'At risk', color: 'warning' },
  OFF_TRACK: { label: 'Off track', color: 'error' },
};

export const STATUS = {
  ACTIVE: { label: 'Active', color: 'info' },
  ACHIEVED: { label: 'Achieved', color: 'success' },
  NOT_ACHIEVED: { label: 'Not achieved', color: 'error' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

export const OBJECTIVES_MET = { YES: 'Yes', PARTLY: 'Partly', NO: 'No' };

export const REVIEW_STATUS = {
  PLANNED: { label: 'Planned', color: 'info' },
  COMPLETED: { label: 'Completed', color: 'success' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

/** A measure's value with its unit: 12,000 customers, 24 %, 9.1 % of energy. */
export function formatValue(value, unit) {
  if (value === null || value === undefined) return '—';
  const n = Number(value);
  const text = n.toLocaleString('en-KE', { maximumFractionDigits: 2 });
  if (!unit) return text;
  return unit.startsWith('%') ? `${text}${unit}` : `${text} ${unit}`;
}

export function formatPct(value) {
  if (value === null || value === undefined) return '—';
  return `${Math.round(Number(value))}%`;
}

/** Progress clamped to 0-100 for a bar. */
export function barValue(pct) {
  if (pct === null || pct === undefined) return 0;
  return Math.max(0, Math.min(100, Number(pct)));
}

/**
 * Points for a small line chart of measurements, scaled into a width x height box. The range always
 * includes the baseline and the target so the line reads against both.
 */
export function sparkPoints(benefit, width, height) {
  const ms = benefit.measurements ?? [];
  const base = new Date(`${benefit.baselineDate}T00:00:00Z`).getTime();
  const end = new Date(`${benefit.targetDate}T00:00:00Z`).getTime();
  const values = [Number(benefit.baselineValue), Number(benefit.targetValue), ...ms.map((m) => Number(m.value))];
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  const span = hi - lo || 1;
  const x = (t) => (Math.max(0, Math.min(1, (t - base) / (end - base || 1))) * width);
  const y = (v) => height - ((v - lo) / span) * height;
  const pts = [{ x: 0, y: y(Number(benefit.baselineValue)) }].concat(
    ms.map((m) => ({ x: x(new Date(`${m.measuredOn}T00:00:00Z`).getTime()), y: y(Number(m.value)) })),
  );
  return { line: pts, target: { x: width, y: y(Number(benefit.targetValue)) }, baselineY: y(Number(benefit.baselineValue)) };
}
