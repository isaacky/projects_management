import { describe, expect, it } from 'vitest';
import { matchesUser, userForm, userRequest, validateUser } from './users.js';

describe('users', () => {
  it('validates the form', () => {
    expect(validateUser({ email: 'nope', displayName: ' ', roles: ['PMO_LEAD'], active: true })).toEqual({
      email: 'Enter the email address they sign in with',
      displayName: 'Enter their name',
    });
    expect(validateUser({ email: 'a@b.co', displayName: 'A', roles: ['pmo lead'], active: true }).roles).toBe(
      '"pmo lead" is not a valid role code',
    );
    expect(validateUser({ email: 'a@b.co', displayName: 'A', roles: [], active: true })).toEqual({});
  });

  it('builds a clean request', () => {
    expect(
      userRequest({ email: ' Jane@KPLC.example ', displayName: ' Jane ', roles: ['PMO_LEAD', 'pmo lead', 'CONTRACT_MANAGER'], active: false }),
    ).toEqual({ email: 'jane@kplc.example', displayName: 'Jane', roles: ['CONTRACT_MANAGER', 'PMO_LEAD'], active: false });
  });

  it('copies a user into the form', () => {
    const u = { email: 'a@b.co', displayName: 'A', roles: ['X_Y'], active: false };
    const f = userForm(u);
    f.roles.push('Z');
    expect(u.roles).toEqual(['X_Y']);
    expect(userForm(null)).toEqual({ email: '', displayName: '', roles: [], active: true });
  });

  it('filters by name, email or role', () => {
    const u = { displayName: 'Jane Doe', email: 'jane@x.co', roles: ['FINANCE_OFFICER'] };
    expect(matchesUser(u, 'finance')).toBe(true);
    expect(matchesUser(u, 'JANE@')).toBe(true);
    expect(matchesUser(u, 'bob')).toBe(false);
    expect(matchesUser(u, '  ')).toBe(true);
  });
});
