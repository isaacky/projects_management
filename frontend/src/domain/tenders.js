export const TENDER_STATUS = {
  DRAFT: { label: 'Draft', color: 'default' },
  PUBLISHED: { label: 'Open for bids', color: 'info' },
  CLOSED: { label: 'Closed, sealed', color: 'warning' },
  TECHNICAL_OPENED: { label: 'Technical opened', color: 'secondary' },
  OPENED: { label: 'Opened', color: 'secondary' },
  AWARDED: { label: 'Awarded', color: 'success' },
  TERMINATED: { label: 'Terminated', color: 'default' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

/** The status to show: a published tender past its closing time is "closed, sealed". */
export function displayStatus(t) {
  return t.status === 'PUBLISHED' && t.closed ? 'CLOSED' : t.status;
}

export const ENVELOPE_LABEL = {
  SINGLE: 'Bid',
  TECHNICAL: 'Technical',
  FINANCIAL: 'Financial',
};

/** "3 days 4 h", "2 h 10 min", "45 min", or null if past. */
export function timeLeft(untilIso, nowMs) {
  const ms = new Date(untilIso).getTime() - nowMs;
  if (!(ms > 0)) return null;
  const min = Math.floor(ms / 60_000);
  const days = Math.floor(min / 1440);
  const hours = Math.floor((min % 1440) / 60);
  const mins = min % 60;
  if (days > 0) return `${days} day${days === 1 ? '' : 's'} ${hours} h`;
  if (hours > 0) return `${hours} h ${mins} min`;
  return `${Math.max(mins, 1)} min`;
}

/** ISO instant -> value for <input type="datetime-local"> in the browser's time zone. */
export function toLocalInput(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** <input type="datetime-local"> value -> ISO instant (UTC). */
export function fromLocalInput(value) {
  return value ? new Date(value).toISOString() : null;
}

export function formatDateTime(iso) {
  return iso
    ? new Date(iso).toLocaleString('en-KE', { dateStyle: 'medium', timeStyle: 'short' })
    : '—';
}

export function formatBytes(n) {
  if (n == null) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}
