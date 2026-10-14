import { toMinorUnits } from './money.js';

/** Minor units -> editable text in major units, e.g. 150000000 -> "1500000". */
export function minorToInput(minor) {
  if (minor === null || minor === undefined) return '';
  const whole = Math.trunc(minor / 100);
  const cents = Math.abs(minor % 100);
  return cents ? `${whole}.${String(cents).padStart(2, '0')}` : String(whole);
}

/** Editable text -> minor units; blank -> null (no limit); invalid -> NaN. */
export function inputToMinor(text) {
  if (String(text ?? '').trim() === '') return null;
  return toMinorUnits(text);
}
