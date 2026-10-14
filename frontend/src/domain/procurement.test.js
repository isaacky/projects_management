import { describe, expect, it } from 'vitest';
import { roleLabel, toRoleCode } from './procurement.js';
import { inputToMinor, minorToInput } from '../utils/amountField.js';

describe('role codes', () => {
  it('turns typed names into codes and back', () => {
    expect(toRoleCode('Accounting officer')).toBe('ACCOUNTING_OFFICER');
    expect(toRoleCode('  head of  procurement ')).toBe('HEAD_OF_PROCUREMENT');
    expect(roleLabel('ACCOUNTING_OFFICER')).toBe('Accounting officer');
    expect(roleLabel(null)).toBe('—');
  });
});

describe('amount fields', () => {
  it('round-trips minor units through the input text', () => {
    expect(minorToInput(150000000)).toBe('1500000');
    expect(minorToInput(1050)).toBe('10.50');
    expect(minorToInput(null)).toBe('');
    expect(inputToMinor('')).toBeNull();
    expect(inputToMinor('1,500,000')).toBe(150000000);
    expect(inputToMinor('abc')).toBeNaN();
  });
});
