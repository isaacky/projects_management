// Display helpers for bid evaluation. Pure functions, unit-tested.

export const EVALUATION_STEPS = [
  { status: 'SETUP', label: 'Set-up' },
  { status: 'PRELIMINARY', label: 'Preliminary' },
  { status: 'TECHNICAL', label: 'Technical' },
  { status: 'FINANCIAL', label: 'Financial' },
  { status: 'REPORT', label: 'Report' },
  { status: 'SIGNING', label: 'Signatures' },
  { status: 'APPROVAL', label: 'Approval' },
  { status: 'AWARDED', label: 'Award' },
];

export const EVALUATION_STATUS = {
  SETUP: { label: 'Being set up', color: 'default' },
  PRELIMINARY: { label: 'Preliminary evaluation', color: 'info' },
  TECHNICAL: { label: 'Technical evaluation', color: 'info' },
  FINANCIAL: { label: 'Financial evaluation', color: 'info' },
  REPORT: { label: 'Report being prepared', color: 'secondary' },
  SIGNING: { label: 'Report awaiting signatures', color: 'secondary' },
  APPROVAL: { label: 'Awaiting approval', color: 'warning' },
  AWARDED: { label: 'Awarded', color: 'success' },
  TERMINATED: { label: 'Terminated', color: 'default' },
};

export const COMMITTEE_ROLE = {
  CHAIR: 'Chair',
  MEMBER: 'Member',
  SECRETARY: 'Secretary (does not vote)',
};

export const STAGE_LABEL = {
  PRELIMINARY: 'Preliminary (pass/fail)',
  TECHNICAL: 'Technical (scored)',
  FINANCIAL: 'Financial',
};

export const RESERVED_GROUP = {
  YOUTH: 'Youth',
  WOMEN: 'Women',
  PWD: 'Persons with disabilities',
  AGPO: 'Any AGPO group',
};

export const ROLE_LABEL = {
  HEAD_OF_PROCUREMENT: 'Head of procurement',
  ACCOUNTING_OFFICER: 'Accounting officer',
};

/** Whether a supplier may take part in a tender reserved for an AGPO group (null = not reserved). */
export function eligibleFor(group, supplier) {
  if (!group) return true;
  return Boolean(supplier.agpoValid && supplier.agpoCategory && (group === 'AGPO' || group === supplier.agpoCategory));
}

/** Index of the current step in EVALUATION_STEPS (terminated shows as the last step). */
export function stepIndex(status) {
  if (status === 'TERMINATED') return EVALUATION_STEPS.length - 1;
  return Math.max(0, EVALUATION_STEPS.findIndex((s) => s.status === status));
}

export function criteriaOf(criteria, stage) {
  return (criteria ?? []).filter((c) => c.stage === stage).sort((a, b) => a.sortOrder - b.sortOrder);
}

export function technicalTotal(criteria) {
  return criteriaOf(criteria, 'TECHNICAL').reduce((sum, c) => sum + Number(c.maxScore ?? 0), 0);
}

/** Marks keyed by criterion id for one bid (and optionally one member). */
export function marksByCriterion(marks, bidId, memberId) {
  const out = {};
  (marks ?? [])
    .filter((m) => m.bidId === bidId && (!memberId || m.memberId === memberId))
    .forEach((m) => {
      out[m.criterionId] = m;
    });
  return out;
}

/** How many of the stage's criteria the member has marked for a bid. */
export function markedCount(myMarks, bidId, criteria) {
  const mine = marksByCriterion(myMarks, bidId);
  return criteria.filter((c) => mine[c.id]).length;
}

/** Problems with a proposed committee, in plain words; empty when it can be appointed. */
export function committeeIssues(members, minMembers, maxMembers) {
  const issues = [];
  const voting = members.filter((m) => m.role !== 'SECRETARY').length;
  const chairs = members.filter((m) => m.role === 'CHAIR').length;
  const secretaries = members.filter((m) => m.role === 'SECRETARY').length;
  if (chairs !== 1) issues.push('Choose exactly one chair');
  if (secretaries > 1) issues.push('Only one secretary');
  if (voting < minMembers || voting > maxMembers) {
    issues.push(`Between ${minMembers} and ${maxMembers} voting members (now ${voting})`);
  }
  return issues;
}

/** A technical score as typed: a number from 0 to max with at most 2 decimals. */
export function validScore(text, max) {
  const s = String(text ?? '').trim();
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return false;
  const n = Number(s);
  return n >= 0 && n <= Number(max);
}

/**
 * The committee's view of one criterion for one bid from closed-stage marks:
 * preliminary -> { met, notMet }; technical -> { average } (2 decimals).
 */
export function criterionSummary(marks, criterion, bidId) {
  const rows = (marks ?? []).filter((m) => m.criterionId === criterion.id && m.bidId === bidId);
  if (criterion.stage === 'PRELIMINARY') {
    return { met: rows.filter((m) => m.passed).length, notMet: rows.filter((m) => m.passed === false).length };
  }
  if (!rows.length) return { average: null };
  const sum = rows.reduce((s, m) => s + Number(m.score), 0);
  return { average: Math.round((sum / rows.length) * 100) / 100 };
}

/** Why a bid lost, for the regret letter. */
export function regretReason(bid, winner, currency, formatMoney) {
  if (bid.disqualifiedStage === 'PRELIMINARY') {
    return `Your bid did not meet the mandatory requirements of the tender. ${bid.disqualifiedReason ?? ''}`.trim();
  }
  if (bid.disqualifiedStage === 'TECHNICAL') {
    return `Your bid did not reach the technical pass mark. ${bid.disqualifiedReason ?? ''}`.trim();
  }
  if (bid.disqualifiedStage === 'FINANCIAL') {
    return `Your bid was rejected at the financial evaluation: ${bid.disqualifiedReason ?? ''}`.trim();
  }
  if (winner) {
    return `Your bid was responsive but was not the lowest evaluated bid (your evaluated price: ${formatMoney(
      bid.evaluatedPriceMinor,
      currency,
    )}).`;
  }
  return 'The tender was not awarded.';
}
