// Labels for procurement settings. Mirrors the backend enums.

export const APPROVAL_STAGES = [
  { key: 'REQUISITION', label: 'Requisition' },
  { key: 'AWARD', label: 'Contract award' },
  { key: 'CONTRACT_VARIATION', label: 'Contract variation' },
];

export const REPORT_SIGNATORIES = [
  { key: 'ALL_MEMBERS', label: 'All committee members' },
  { key: 'CHAIR_AND_SECRETARY', label: 'Chair and secretary' },
  { key: 'CHAIR_ONLY', label: 'Chair only' },
];

export function stageLabel(key) {
  return APPROVAL_STAGES.find((s) => s.key === key)?.label ?? key;
}

export function signatoriesLabel(key) {
  return REPORT_SIGNATORIES.find((s) => s.key === key)?.label ?? key;
}

/** ACCOUNTING_OFFICER -> "Accounting officer" */
export function roleLabel(code) {
  if (!code) return '—';
  const words = code.toLowerCase().split('_');
  return words.map((w, i) => (i === 0 ? w[0].toUpperCase() + w.slice(1) : w)).join(' ');
}

/** "Accounting officer" or "accounting_officer" -> "ACCOUNTING_OFFICER" */
export function toRoleCode(text) {
  return String(text ?? '')
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}
