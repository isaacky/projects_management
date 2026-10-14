// Helpers for schedules, risks and status reports. Pure functions, unit-tested.

export const RAG = {
  GREEN: { label: 'Green', color: 'success' },
  AMBER: { label: 'Amber', color: 'warning' },
  RED: { label: 'Red', color: 'error' },
};

export const RISK_LEVEL = {
  LOW: { label: 'Low', color: 'success' },
  MEDIUM: { label: 'Medium', color: 'info' },
  HIGH: { label: 'High', color: 'warning' },
  CRITICAL: { label: 'Critical', color: 'error' },
};

export const RISK_CATEGORY = {
  TECHNICAL: 'Technical',
  COMMERCIAL: 'Commercial',
  SCHEDULE: 'Schedule',
  SAFETY: 'Health and safety',
  ENVIRONMENTAL: 'Environmental',
  WAYLEAVE: 'Wayleave / land',
  REGULATORY: 'Regulatory',
  STAKEHOLDER: 'Stakeholder / community',
  SECURITY: 'Security / vandalism',
  OTHER: 'Other',
};

export const RISK_RESPONSE = { AVOID: 'Avoid', MITIGATE: 'Mitigate', TRANSFER: 'Transfer', ACCEPT: 'Accept' };

export const ISSUE_PRIORITY = {
  LOW: { label: 'Low', color: 'default' },
  MEDIUM: { label: 'Medium', color: 'info' },
  HIGH: { label: 'High', color: 'warning' },
  CRITICAL: { label: 'Critical', color: 'error' },
};

export const ISSUE_STATUS = { OPEN: 'Open', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved', CLOSED: 'Closed' };

/** Same bands as the server: 1-4 low, 5-9 medium, 10-16 high, 20-25 critical. */
export function riskLevel(probability, impact) {
  const s = probability * impact;
  return s >= 20 ? 'CRITICAL' : s >= 10 ? 'HIGH' : s >= 5 ? 'MEDIUM' : 'LOW';
}

const DAY = 86_400_000;

export function daysBetween(fromIso, toIso) {
  return Math.round((Date.parse(`${toIso}T00:00:00Z`) - Date.parse(`${fromIso}T00:00:00Z`)) / DAY);
}

export function addDays(iso, n) {
  return new Date(Date.parse(`${iso}T00:00:00Z`) + n * DAY).toISOString().slice(0, 10);
}

/**
 * "3, 5SS+2; 7FS-1" -> [{ row: 3, type: 'FS', lagDays: 0 }, ...]. Throws with a readable message
 * on anything it cannot read. Row numbers are as shown in the editor (1-based).
 */
export function parsePredecessors(text) {
  const s = String(text ?? '').trim();
  if (!s) return [];
  return s.split(/[,;]/).map((part) => {
    const m = part.trim().toUpperCase().match(/^(\d+)\s*(FS|SS)?\s*([+-]\s*\d+)?\s*D?$/);
    if (!m) throw new Error(`"${part.trim()}" is not a predecessor (use e.g. 3, 3FS+2 or 5SS)`);
    return { row: Number(m[1]), type: m[2] ?? 'FS', lagDays: m[3] ? Number(m[3].replace(/\s/g, '')) : 0 };
  });
}

/** [{ row, type, lagDays }] -> "3, 5SS+2" */
export function formatPredecessors(preds) {
  return preds
    .map((p) => `${p.row}${p.type === 'FS' && !p.lagDays ? '' : p.type}${p.lagDays > 0 ? `+${p.lagDays}` : p.lagDays < 0 ? p.lagDays : ''}`)
    .join(', ');
}

/**
 * Editor rows (with a 0-based level) -> each row's parent index, or an error message when a row
 * is indented more than one level below the row above it.
 */
export function parentIndexes(rows) {
  const stack = [];
  return rows.map((r, i) => {
    if (i === 0 && r.level > 0) throw new Error('The first row cannot be indented');
    while (stack.length && rows[stack[stack.length - 1]].level >= r.level) stack.pop();
    const parent = stack.length ? stack[stack.length - 1] : null;
    const parentLevel = parent === null ? -1 : rows[parent].level;
    if (r.level > parentLevel + 1) throw new Error(`Row ${i + 1} is indented too far`);
    stack.push(i);
    return parent;
  });
}

export function pct(v) {
  return v == null ? '—' : `${Math.round(Number(v) * 10) / 10}%`;
}
