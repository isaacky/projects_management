// People and roles (Administration > Users and roles).

import { toRoleCode } from './procurement.js';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const ROLE = /^[A-Z][A-Z0-9_]{1,63}$/;

export function emptyUserForm() {
  return { email: '', displayName: '', roles: [], active: true };
}

export function userForm(user) {
  return user
    ? { email: user.email, displayName: user.displayName, roles: [...user.roles], active: user.active }
    : emptyUserForm();
}

/** Field errors for the form, or {} when it can be saved. */
export function validateUser(form) {
  const errors = {};
  if (!EMAIL.test(form.email.trim())) errors.email = 'Enter the email address they sign in with';
  if (!form.displayName.trim()) errors.displayName = 'Enter their name';
  const bad = form.roles.find((r) => !ROLE.test(r));
  if (bad) errors.roles = `"${bad}" is not a valid role code`;
  return errors;
}

/** The request body: trimmed, email in lower case, role codes normalised and without repeats. */
export function userRequest(form) {
  return {
    email: form.email.trim().toLowerCase(),
    displayName: form.displayName.trim(),
    roles: [...new Set(form.roles.map(toRoleCode).filter(Boolean))].sort(),
    active: form.active,
  };
}

/** Role codes to offer: the built-in ones first, then any others in use. */
export function roleOptions(roles) {
  return (roles ?? []).map((r) => r.code);
}

/** Text filter over name, email and roles. */
export function matchesUser(user, query) {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return [user.displayName, user.email, ...(user.roles ?? [])].some((v) => String(v).toLowerCase().includes(q));
}
