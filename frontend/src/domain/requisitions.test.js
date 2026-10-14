import { describe, expect, it } from 'vitest';
import { lineTotalMinor } from './requisitions.js';

describe('lineTotalMinor', () => {
  it('multiplies and rounds to the cent like the backend', () => {
    expect(lineTotalMinor(2, 150_000_000)).toBe(300_000_000);
    expect(lineTotalMinor(12.5, 3_333)).toBe(41_663); // 41,662.5 rounds up
    expect(lineTotalMinor('abc', 100)).toBe(0);
  });
});
