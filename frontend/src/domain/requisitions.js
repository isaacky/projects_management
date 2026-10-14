export const REQUISITION_STATUS = {
  DRAFT: { label: 'Draft', color: 'default' },
  SUBMITTED: { label: 'Awaiting approval', color: 'info' },
  APPROVED: { label: 'Approved', color: 'success' },
  REJECTED: { label: 'Rejected', color: 'error' },
  RETURNED: { label: 'Returned for changes', color: 'warning' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

export const STEP_STATUS = {
  WAITING: 'Waiting',
  PENDING: 'Their turn',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  RETURNED: 'Returned',
  CANCELLED: 'Not needed',
};

/** Quantity × unit price in minor units, rounded to the cent like the backend. */
export function lineTotalMinor(quantity, unitPriceMinor) {
  const q = Number(quantity);
  const p = Number(unitPriceMinor);
  if (!Number.isFinite(q) || !Number.isFinite(p)) return 0;
  return Math.round(q * p);
}
