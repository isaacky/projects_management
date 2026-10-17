// Project objectives and how a post-implementation review judges them.

export const MAX_OBJECTIVES = 20;

export const OUTCOME = {
  MET: { label: 'Met', color: 'success' },
  PARTLY_MET: { label: 'Partly met', color: 'warning' },
  NOT_MET: { label: 'Not met', color: 'error' },
  TOO_EARLY: { label: 'Too early to tell', color: 'default' },
};

/** The rows of the new-project form as the API takes them: trimmed, blank rows dropped. */
export function objectiveInputs(rows) {
  return (rows ?? [])
    .map((r) => ({
      statement: (r.statement ?? '').trim(),
      successMeasure: (r.successMeasure ?? '').trim() || null,
    }))
    .filter((r) => r.statement);
}

/**
 * The review's overall "objectives met" from the per-objective outcomes (the server applies the same rule):
 * YES when every decided objective was met, NO when none was met even partly, PARTLY otherwise. Outcomes
 * too early to tell are left out; null when nothing is decided, so the reviewer's own answer stands.
 */
export function overallFromOutcomes(outcomes) {
  const decided = (outcomes ?? []).filter((o) => o && o !== 'TOO_EARLY');
  if (!decided.length) return null;
  if (decided.every((o) => o === 'MET')) return 'YES';
  if (decided.every((o) => o === 'NOT_MET')) return 'NO';
  return 'PARTLY';
}

/** Objectives the review found partly met or not met: candidates for a lesson learned. */
export function shortfalls(assessments) {
  return (assessments ?? []).filter((a) => a.outcome === 'PARTLY_MET' || a.outcome === 'NOT_MET');
}

/** Starting values for a lesson learned from an objective the review found was not (fully) met. */
export function lessonDraft(assessment, reviewTitle) {
  const what = OUTCOME[assessment.outcome]?.label.toLowerCase() ?? 'not met';
  const title = `O${assessment.objectiveNumber} ${what}: ${assessment.statement}`;
  return {
    kind: 'IMPROVE',
    category: 'OTHER',
    title: title.length > 200 ? `${title.slice(0, 199)}…` : title,
    whatHappened: [
      `${reviewTitle} found the objective "${assessment.statement}" ${what}.`,
      assessment.evidence ? `Evidence: ${assessment.evidence}` : null,
    ]
      .filter(Boolean)
      .join(' '),
  };
}
