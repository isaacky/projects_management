// The signed-in session on a server: tokens from Keycloak, kept in memory only (a reload signs in
// again silently while the Keycloak session lasts). Development builds skip all of this and use the
// "Viewing as" switcher instead.

import {
  authorizeUrl,
  codeChallenge,
  decodeClaims,
  logoutUrl,
  needsRefresh,
  randomString,
  safeReturnPath,
  tokenRequestBody,
  tokensFrom,
} from './oidc.js';

const PENDING_KEY = 'pmp.signin';
const ACTIVE_WINDOW_MS = 25 * 60_000; // keep the session alive only for someone who used the app recently

let config = null; // { issuer, clientId, endpoints }
let tokens = null;
let refreshing = null;
let refreshTimer = null;
let lastActivity = Date.now();

export class SignInError extends Error {
  constructor(message) {
    super(message);
    this.name = 'SignInError';
  }
}

async function loadConfig() {
  // `npm run dev:sso` (mode "sso"): the local Keycloak, even if .env.sso did not come along.
  let issuer =
    import.meta.env.VITE_OIDC_ISSUER || (import.meta.env.MODE === 'sso' ? 'http://localhost:8180/realms/pmp' : '');
  let clientId = import.meta.env.VITE_OIDC_CLIENT_ID || 'pmp-web';
  if (!issuer) {
    // Servers: written by the web container from its environment (see frontend/nginx.conf.template).
    const res = await fetch('/app-config.json', { cache: 'no-store' });
    if (!res.ok) throw new SignInError('The app is not configured for sign-in (app-config.json missing).');
    const json = await res.json();
    issuer = json.oidcIssuer;
    clientId = json.oidcClientId || clientId;
  }
  if (!issuer) throw new SignInError('The app is not configured for sign-in (no identity provider set).');
  const res = await fetch(`${issuer.replace(/\/$/, '')}/.well-known/openid-configuration`);
  if (!res.ok) throw new SignInError('The sign-in service is not reachable. Try again in a few minutes.');
  const d = await res.json();
  return {
    issuer,
    clientId,
    authorize: d.authorization_endpoint,
    token: d.token_endpoint,
    logout: d.end_session_endpoint,
  };
}

function redirectUri() {
  return `${window.location.origin}/`;
}

async function postToken(params) {
  const res = await fetch(config.token, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: tokenRequestBody({ client_id: config.clientId, ...params }),
  });
  const json = await res.json().catch(() => null);
  if (!res.ok) throw new SignInError(json?.error_description || `Sign-in failed (${res.status}).`);
  return tokensFrom(json, Date.now());
}

function store(next) {
  tokens = next;
  clearTimeout(refreshTimer);
  // Refresh a minute before expiry while the person is active, so a long form is not lost to an
  // expired session; an idle tab is left to expire.
  const wait = Math.max(5_000, tokens.expiresAt - Date.now() - 60_000);
  refreshTimer = setTimeout(() => {
    if (Date.now() - lastActivity < ACTIVE_WINDOW_MS) getAccessToken().catch(() => {});
  }, wait);
}

/** Sends the browser to the sign-in page; comes back to `returnTo`. Never resolves. */
export async function signIn(returnTo = window.location.pathname + window.location.search) {
  const verifier = randomString(window.crypto, 48);
  const state = randomString(window.crypto);
  const nonce = randomString(window.crypto);
  try {
    window.sessionStorage.setItem(
      PENDING_KEY,
      JSON.stringify({ state, verifier, nonce, returnTo: safeReturnPath(returnTo) }),
    );
  } catch {
    throw new SignInError('Sign-in needs browser storage. Leave private browsing, or allow site data.');
  }
  const challenge = await codeChallenge(window.crypto, verifier);
  window.location.assign(
    authorizeUrl(config.authorize, { clientId: config.clientId, redirectUri: redirectUri(), state, nonce, challenge }),
  );
  return new Promise(() => {});
}

/**
 * Starts the session before the app renders: finishes a sign-in coming back from Keycloak, or
 * starts one. Resolves once there is an access token.
 */
export async function initSession() {
  config = await loadConfig();
  ['keydown', 'pointerdown', 'wheel'].forEach((type) =>
    window.addEventListener(type, () => (lastActivity = Date.now()), { passive: true, capture: true }),
  );

  const url = new URL(window.location.href);
  const code = url.searchParams.get('code');
  const state = url.searchParams.get('state');
  const error = url.searchParams.get('error');
  if (!code && !error) return signIn();

  let pending = null;
  try {
    pending = JSON.parse(window.sessionStorage.getItem(PENDING_KEY) || 'null');
    window.sessionStorage.removeItem(PENDING_KEY);
  } catch {
    pending = null;
  }
  if (error) {
    window.history.replaceState(null, '', '/');
    throw new SignInError(url.searchParams.get('error_description') || `Sign-in was not completed (${error}).`);
  }
  if (!pending || pending.state !== state) {
    // An old or foreign sign-in response: start again rather than trust it.
    window.history.replaceState(null, '', '/');
    return signIn('/');
  }
  const next = await postToken({
    grant_type: 'authorization_code',
    code,
    redirect_uri: redirectUri(),
    code_verifier: pending.verifier,
  });
  const claims = next.idToken ? decodeClaims(next.idToken) : null;
  if (claims && claims.nonce !== pending.nonce) throw new SignInError('Sign-in response did not match. Try again.');
  store(next);
  window.history.replaceState(null, '', safeReturnPath(pending.returnTo));
}

/** A current access token, refreshed when about to expire; signs in again if the session has ended. */
export async function getAccessToken() {
  if (!needsRefresh(tokens, Date.now())) return tokens.accessToken;
  if (!refreshing) {
    refreshing = (async () => {
      try {
        if (!tokens?.refreshToken) throw new SignInError('Session ended.');
        store(await postToken({ grant_type: 'refresh_token', refresh_token: tokens.refreshToken }));
        return tokens.accessToken;
      } catch {
        tokens = null;
        return signIn();
      } finally {
        refreshing = null;
      }
    })();
  }
  return refreshing;
}

/** After a 401: the token was refused, so force a refresh (or a new sign-in). */
export function expireAccessToken() {
  if (tokens) tokens = { ...tokens, expiresAt: 0 };
}

/** Name and email from the identity provider, for display while the profile loads. */
export function signedInClaims() {
  return tokens?.idToken ? decodeClaims(tokens.idToken) : null;
}

/** Ends the Keycloak session too, then returns to the app's front page (which signs in again). */
export function signOut() {
  const idToken = tokens?.idToken;
  tokens = null;
  clearTimeout(refreshTimer);
  if (config?.logout) {
    window.location.assign(logoutUrl(config.logout, { clientId: config.clientId, idToken, redirectUri: redirectUri() }));
  } else {
    window.location.assign('/');
  }
}
