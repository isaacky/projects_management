import { describe, expect, it } from 'vitest';
import {
  committeeIssues,
  criterionSummary,
  marksByCriterion,
  regretReason,
  stepIndex,
  technicalTotal,
  validScore,
} from './evaluation.js';

describe('evaluation helpers', () => {
  it('finds the step of each status', () => {
    expect(stepIndex('SETUP')).toBe(0);
    expect(stepIndex('FINANCIAL')).toBe(3);
    expect(stepIndex('TERMINATED')).toBe(7);
  });

  it('adds up technical maximum scores', () => {
    expect(
      technicalTotal([
        { stage: 'PRELIMINARY', sortOrder: 1 },
        { stage: 'TECHNICAL', sortOrder: 2, maxScore: 20 },
        { stage: 'TECHNICAL', sortOrder: 3, maxScore: '25.5' },
      ]),
    ).toBe(45.5);
  });

  it('checks a proposed committee', () => {
    const ok = [
      { role: 'CHAIR' },
      { role: 'MEMBER' },
      { role: 'MEMBER' },
      { role: 'SECRETARY' },
    ];
    expect(committeeIssues(ok, 3, 5)).toEqual([]);
    expect(committeeIssues([{ role: 'MEMBER' }], 3, 5)).toEqual([
      'Choose exactly one chair',
      'Between 3 and 5 voting members (now 1)',
    ]);
  });

  it('validates scores against the maximum', () => {
    expect(validScore('12.5', 20)).toBe(true);
    expect(validScore('20', 20)).toBe(true);
    expect(validScore('20.01', 20)).toBe(false);
    expect(validScore('-1', 20)).toBe(false);
    expect(validScore('1.234', 20)).toBe(false);
    expect(validScore('', 20)).toBe(false);
  });

  it('summarises closed-stage marks', () => {
    const marks = [
      { criterionId: 'p', bidId: 'b', memberId: 'm1', passed: true },
      { criterionId: 'p', bidId: 'b', memberId: 'm2', passed: false },
      { criterionId: 't', bidId: 'b', memberId: 'm1', score: 7 },
      { criterionId: 't', bidId: 'b', memberId: 'm2', score: 8 },
      { criterionId: 't', bidId: 'b', memberId: 'm3', score: 8 },
    ];
    expect(criterionSummary(marks, { id: 'p', stage: 'PRELIMINARY' }, 'b')).toEqual({ met: 1, notMet: 1 });
    expect(criterionSummary(marks, { id: 't', stage: 'TECHNICAL' }, 'b')).toEqual({ average: 7.67 });
    expect(marksByCriterion(marks, 'b', 'm2')).toHaveProperty('t');
  });

  it('explains regret reasons', () => {
    const fmt = (m) => `KES ${m / 100}`;
    expect(regretReason({ disqualifiedStage: 'PRELIMINARY', disqualifiedReason: 'Did not meet: X' }, {}, 'KES', fmt))
      .toContain('mandatory requirements');
    expect(regretReason({ evaluatedPriceMinor: 500 }, { id: 'w' }, 'KES', fmt)).toContain('KES 5');
  });
});
