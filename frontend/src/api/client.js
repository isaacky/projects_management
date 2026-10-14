// Thin fetch wrapper. On servers every request carries the signed-in user's access token (which
// also says the tenant). In development the tenant travels in the X-Tenant-ID header and the
// "Viewing as" switcher picks the user, unless the app runs in sign-in mode (VITE_AUTH=oidc,
// `npm run dev:sso`) against the local Keycloak.

import { expireAccessToken, getAccessToken } from '../auth/session.js';

/** Sign in through Keycloak even in a development build. */
const FORCE_SIGN_IN = import.meta.env.VITE_AUTH === 'oidc' || import.meta.env.MODE === 'sso';

// Falls back to the sample "Kenya Power (pilot)" tenant in development if .env.local is missing.
const SAMPLE_TENANT = '01920000-0000-7000-8000-000000000001';
const DEV_TENANT = FORCE_SIGN_IN
  ? undefined
  : import.meta.env.VITE_DEV_TENANT_ID || (import.meta.env.DEV ? SAMPLE_TENANT : undefined);

/** True when talking to a development backend (tenant and user chosen by the app, not a login). */
export const DEV_MODE = Boolean(DEV_TENANT);

const DEV_USER_KEY = 'pmp.devUserId';

/** Development "Viewing as" user. Stored per browser; blank = the backend's default user. */
export function getDevUserId() {
  if (!DEV_MODE) return null;
  try {
    return window.localStorage.getItem(DEV_USER_KEY);
  } catch {
    return null;
  }
}

export function setDevUserId(id) {
  try {
    if (id) window.localStorage.setItem(DEV_USER_KEY, id);
    else window.localStorage.removeItem(DEV_USER_KEY);
  } catch {
    // storage unavailable (private window): the choice lasts until reload
  }
}

export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
    this.fieldErrors = problem?.errors || {};
  }
}

async function identityHeaders() {
  const headers = {};
  if (DEV_MODE) {
    headers['X-Tenant-ID'] = DEV_TENANT;
    const devUser = getDevUserId();
    if (devUser) headers['X-Dev-User-ID'] = devUser;
  } else {
    headers.Authorization = `Bearer ${await getAccessToken()}`;
  }
  return headers;
}

/**
 * fetch with the identity headers. A 401 means the token was refused (expired early, or the
 * signing key rotated): refresh once and retry, which signs in again if the session has ended.
 */
async function send(path, init) {
  let res = await fetch(path, { ...init, headers: { ...init.headers, ...(await identityHeaders()) } });
  if (res.status === 401 && !DEV_MODE) {
    expireAccessToken();
    res = await fetch(path, { ...init, headers: { ...init.headers, ...(await identityHeaders()) } });
  }
  return res;
}

/** Sends a multipart form (file uploads). Do not set Content-Type: the browser adds the boundary. */
export async function apiUpload(path, formData, { method = 'POST', signal } = {}) {
  const res = await send(path, {
    method,
    headers: { Accept: 'application/json' },
    body: formData,
    signal,
  });
  return handle(res);
}

/** Downloads a file through the API (with identity headers) and saves it in the browser. */
export async function downloadFile(path, fallbackName = 'download') {
  const res = await send(path, { headers: {} });
  if (!res.ok) {
    const isJson = (res.headers.get('content-type') || '').includes('json');
    throw new ApiError(res.status, isJson ? await res.json() : null);
  }
  const disposition = res.headers.get('content-disposition') || '';
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  const name = utf8 ? decodeURIComponent(utf8[1]) : plain ? plain[1] : fallbackName;
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

export async function apiFetch(path, { method = 'GET', body, signal } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await send(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  });
  return handle(res);
}

async function handle(res) {
  if (res.status === 204) return null;
  const isJson = (res.headers.get('content-type') || '').includes('json');
  const data = isJson ? await res.json() : null;
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}
