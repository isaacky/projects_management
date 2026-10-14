// Labels and small calculations for contracts. Pure functions, unit-tested.

export const CONTRACT_STATUS = {
  DRAFT: { label: 'Draft', color: 'default' },
  ACTIVE: { label: 'Active', color: 'info' },
  COMPLETED: { label: 'Completed', color: 'success' },
  TERMINATED: { label: 'Terminated', color: 'error' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

export const INVOICE_STATUS = {
  RECEIVED: { label: 'To certify', color: 'warning' },
  CERTIFIED: { label: 'To approve', color: 'warning' },
  APPROVED: { label: 'To pay', color: 'info' },
  PAID: { label: 'Paid', color: 'success' },
  REJECTED: { label: 'Rejected', color: 'default' },
};

export const MILESTONE_STATUS = {
  PLANNED: { label: 'Planned', color: 'default' },
  ACCEPTED: { label: 'Accepted', color: 'success' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

export const SECURITY_KIND = { PERFORMANCE: 'Performance security', ADVANCE_PAYMENT: 'Advance payment guarantee' };
export const SECURITY_FORM = { BANK_GUARANTEE: 'Bank guarantee', INSURANCE_BOND: 'Insurance bond', OTHER: 'Other' };
export const SECURITY_STATUS = { HELD: 'Held', RELEASED: 'Released', CALLED: 'Called' };

export const DOCUMENT_KIND = {
  SIGNED_CONTRACT: 'Signed contract',
  SECURITY: 'Security',
  INSPECTION: 'Inspection / acceptance',
  INVOICE: 'Invoice',
  VARIATION: 'Variation',
  OTHER: 'Other',
};

export const REVIEW_STATUS = {
  NONE: 'None',
  LODGED: 'Request for review lodged',
  DISMISSED: 'Review dismissed',
  UPHELD: 'Review upheld',
};

/** 1000 basis points -> "10%". */
export function bpToPercent(bp) {
  return `${Math.round(bp) / 100}%`;
}

/** "10" or "7.5" (percent) -> basis points; invalid -> NaN. */
export function percentToBp(text) {
  const s = String(text ?? '').trim();
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return NaN;
  return Math.round(Number(s) * 100);
}

/** Share of a total as a whole percentage, 0 when the total is 0. */
export function share(part, total) {
  return total > 0 ? Math.round((part / total) * 100) : 0;
}

/** Retention on a gross amount, rounded half up to the cent, like the server. */
export function retention(grossMinor, bp) {
  return Math.floor((grossMinor * bp + 5000) / 10000);
}

/** Today in the browser's time zone, as YYYY-MM-DD for date inputs. */
export function todayInput() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function formatDate(iso) {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('en-KE', { dateStyle: 'medium' }) : '—';
}

/** Totals for a set of contract summaries, e.g. on a project. */
export function portfolioTotals(contracts) {
  const live = (contracts ?? []).filter((c) => ['ACTIVE', 'COMPLETED', 'TERMINATED'].includes(c.status));
  return {
    committed: live.reduce((s, c) => s + c.currentValueMinor, 0),
    certified: live.reduce((s, c) => s + c.certifiedMinor, 0),
    paid: live.reduce((s, c) => s + c.paidMinor, 0),
    count: live.length,
  };
}
