// Annual procurement plan: labels, financial-year helpers, filtering and CSV export.

export const PLAN_STATUS = {
  DRAFT: { label: 'Being prepared', color: 'default' },
  SUBMITTED: { label: 'With the accounting officer', color: 'warning' },
  APPROVED: { label: 'Approved', color: 'success' },
};

export const DEPT_STATUS = {
  DRAFT: { label: 'Preparing', color: 'default' },
  SUBMITTED: { label: 'Submitted', color: 'success' },
};

export const CATEGORY = {
  GOODS: 'Goods',
  WORKS: 'Works',
  NON_CONSULTING: 'Non-consulting services',
  CONSULTANCY: 'Consultancy',
};

export const RESERVED = {
  YOUTH: 'Youth',
  WOMEN: 'Women',
  PWD: 'Persons with disabilities',
  AGPO: 'Any AGPO group',
};

export const STAGE = {
  NOT_STARTED: { label: 'Not started', color: 'default' },
  REQUISITIONED: { label: 'Requisitioned', color: 'info' },
  TENDERING: { label: 'Tendering', color: 'warning' },
  AWARDED: { label: 'Awarded', color: 'success' },
};

const pad = (n) => String(n).padStart(2, '0');

/** "2026/27" for a year starting 2026-07-01; "2027" for one starting in January. */
export function fyLabel(fyStart) {
  if (!fyStart) return '';
  const [y, m] = fyStart.split('-').map(Number);
  return m === 1 ? String(y) : `${y}/${pad((y + 1) % 100)}`;
}

/** The next July-to-June year to plan for, as yyyy-mm-dd. */
export function nextFyStart(today = new Date()) {
  const y = today.getFullYear();
  return `${today.getMonth() < 6 ? y : y + 1}-07-01`;
}

/** Dates of a quarter (1-4) of a financial year starting fyStart, for display. */
export function quarterRange(fyStart, quarter) {
  const [y, m] = fyStart.split('-').map(Number);
  const start = new Date(Date.UTC(y, m - 1 + 3 * (quarter - 1), 1));
  const end = new Date(Date.UTC(y, m - 1 + 3 * quarter, 0));
  const fmt = (d) => d.toLocaleDateString('en-GB', { month: 'short', year: 'numeric', timeZone: 'UTC' });
  return `${fmt(start)} – ${fmt(end)}`;
}

/** filter: { departmentId, quarter, category, stage, q, showDropped, onlyBehind, onlyChanged } */
export function filterLines(lines, filter = {}) {
  const q = (filter.q ?? '').trim().toLowerCase();
  return lines.filter(
    (l) =>
      (filter.showDropped || !l.cancelled) &&
      (!filter.departmentId || l.departmentId === filter.departmentId) &&
      (!filter.quarter || l.quarter === Number(filter.quarter)) &&
      (!filter.category || l.category === filter.category) &&
      (!filter.stage || l.stage === filter.stage) &&
      (!filter.onlyBehind || l.behind) &&
      (!filter.onlyChanged || Boolean(l.change) || l.cancelled) &&
      (!q ||
        l.description.toLowerCase().includes(q) ||
        l.reference.toLowerCase().includes(q) ||
        (l.budgetCode ?? '').toLowerCase().includes(q)),
  );
}

/** Minor units as plain major-unit text for spreadsheets: 123456 -> "1234.56". */
export function plainAmount(minor) {
  if (minor === null || minor === undefined) return '';
  const sign = minor < 0 ? '-' : '';
  const abs = Math.abs(minor);
  return `${sign}${Math.trunc(abs / 100)}.${pad(abs % 100)}`;
}

function csvCell(v) {
  const s = v === null || v === undefined ? '' : String(v);
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

export function toCsv(rows) {
  return rows.map((r) => r.map(csvCell).join(',')).join('\r\n') + '\r\n';
}

/** The consolidated plan as rows (header first), e.g. to open in Excel. */
export function planRows(plan) {
  const header = [
    'Reference',
    'Department',
    'Description',
    'Category',
    'Quantity',
    'Unit',
    'Estimate (KES)',
    'Approved (KES)',
    'Method',
    'Quarter',
    'Source of funds',
    'Budget code',
    'Reserved for',
    'Project',
    'Requisitioned (KES)',
    'Awarded (KES)',
    'Stage',
    'Status',
  ];
  const body = plan.lines.map((l) => [
    l.reference,
    l.department,
    l.description,
    CATEGORY[l.category] ?? l.category,
    l.quantity,
    l.unit,
    plainAmount(l.estimateMinor),
    plainAmount(l.approvedEstimateMinor),
    l.methodCode,
    `Q${l.quarter}`,
    l.sourceOfFunds ?? '',
    l.budgetCode ?? '',
    l.reservedGroup ? RESERVED[l.reservedGroup] : '',
    l.project?.code ?? '',
    plainAmount(l.committedMinor),
    plainAmount(l.awardedMinor),
    STAGE[l.stage]?.label ?? l.stage,
    l.cancelled ? 'Dropped' : l.change === 'NEW' ? 'New' : l.change === 'CHANGED' ? 'Changed' : '',
  ]);
  return [header, ...body];
}

/** A blank import file with the expected columns and one example row. */
export const IMPORT_TEMPLATE = toCsv([
  ['Description', 'Category', 'Quantity', 'Unit', 'Estimate (KES)', 'Method', 'Quarter', 'Source of funds',
    'Budget code', 'Reserved', 'Project code'],
  ['Supply of office chairs', 'Goods', '40', 'each', '600000', 'RFQ', 'Q2', 'Internal resources', 'R-2210',
    'Youth', ''],
]);

/** Saves text as a file in the browser. */
export function saveText(text, fileName, type = 'text/csv;charset=utf-8') {
  // A BOM makes Excel read the file as UTF-8.
  const blob = new Blob(['﻿', text], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
