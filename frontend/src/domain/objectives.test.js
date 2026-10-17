import { describe, expect, it } from 'vitest';
import { lessonDraft, objectiveInputs, overallFromOutcomes, shortfalls } from './objectives.js';

describe('objectiveInputs', () => {
  it('trims and drops blank rows', () => {
    expect(
      objectiveInputs([
        { statement: '  Connect customers ', successMeasure: ' 12,000 by 2028 ' },
        { statement: '   ', successMeasure: 'orphan' },
        { statement: 'Stay in budget', successMeasure: '  ' },
      ]),
    ).toEqual([
      { statement: 'Connect customers', successMeasure: '12,000 by 2028' },
      { statement: 'Stay in budget', successMeasure: null },
    ]);
    expect(objectiveInputs(undefined)).toEqual([]);
  });
});

describe('overallFromOutcomes', () => {
  it('follows the decided outcomes', () => {
    expect(overallFromOutcomes(['MET', 'MET', 'TOO_EARLY'])).toBe('YES');
    expect(overallFromOutcomes(['NOT_MET', 'TOO_EARLY'])).toBe('NO');
    expect(overallFromOutcomes(['MET', 'NOT_MET'])).toBe('PARTLY');
    expect(overallFromOutcomes(['PARTLY_MET'])).toBe('PARTLY');
  });

  it('leaves it to the reviewer when nothing is decided', () => {
    expect(overallFromOutcomes([])).toBeNull();
    expect(overallFromOutcomes(['TOO_EARLY', null])).toBeNull();
  });
});

describe('shortfalls', () => {
  it('picks the objectives not fully met', () => {
    const list = [
      { objectiveNumber: 1, outcome: 'MET' },
      { objectiveNumber: 2, outcome: 'PARTLY_MET' },
      { objectiveNumber: 3, outcome: 'NOT_MET' },
      { objectiveNumber: 4, outcome: 'TOO_EARLY' },
    ];
    expect(shortfalls(list).map((a) => a.objectiveNumber)).toEqual([2, 3]);
  });
});

describe('lessonDraft', () => {
  it('starts a lesson from the finding', () => {
    const d = lessonDraft(
      { objectiveNumber: 2, outcome: 'PARTLY_MET', statement: 'Cut losses', evidence: '7.8% against 6%' },
      '12-month post-implementation review',
    );
    expect(d.kind).toBe('IMPROVE');
    expect(d.title).toBe('O2 partly met: Cut losses');
    expect(d.whatHappened).toBe(
      '12-month post-implementation review found the objective "Cut losses" partly met. Evidence: 7.8% against 6%',
    );
  });

  it('keeps the title within 200 characters', () => {
    const d = lessonDraft({ objectiveNumber: 1, outcome: 'NOT_MET', statement: 'x'.repeat(500) }, 'Review');
    expect(d.title.length).toBe(200);
    expect(d.whatHappened.includes('Evidence')).toBe(false);
  });
});
