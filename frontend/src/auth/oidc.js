// Pure helpers for signing in with OpenID Connect (authorization code flow with PKCE), used by
// session.js. Kept free of browser globals so they can be tested on their own.

/** Bytes -> base64url without padding (RFC 7636). */
export function base64Url(bytes) {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** A random URL-safe string from `byteCount` random bytes (32 bytes -> 43 characters). */
export function randomString(cryptoImpl, byteCount = 32) {
  const bytes = new Uint8Array(byteCount);
  cryptoImpl.getRandomValues(bytes);
  return base64Url(bytes);
}

/** The S256 code challenge for a code verifier. */
export async function codeChallenge(cryptoImpl, verifier) {
  const digest = await cryptoImpl.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

/** The address of the identity provider's sign-in page. */
export function authorizeUrl(endpoint, { clientId, redirectUri, state, nonce, challenge }) {
  const url = new URL(endpoint);
  url.search = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: redirectUri,
    scope: 'openid profile email',
    state,
    nonce,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  }).toString();
  return url.toString();
}

/** The address that signs the person out of the identity provider and returns to the app. */
export function logoutUrl(endpoint, { clientId, idToken, redirectUri }) {
  const url = new URL(endpoint);
  const params = { client_id: clientId, post_logout_redirect_uri: redirectUri };
  if (idToken) params.id_token_hint = idToken;
  url.search = new URLSearchParams(params).toString();
  return url.toString();
}

/** Form body for the token endpoint. */
export function tokenRequestBody(params) {
  return new URLSearchParams(params).toString();
}

/** Token endpoint response -> what the app keeps, with absolute expiry times (ms). */
export function tokensFrom(response, now) {
  if (!response?.access_token) throw new Error('The sign-in service returned no access token.');
  const seconds = (v, fallback) => (Number.isFinite(Number(v)) && Number(v) > 0 ? Number(v) : fallback);
  return {
    accessToken: response.access_token,
    refreshToken: response.refresh_token ?? null,
    idToken: response.id_token ?? null,
    expiresAt: now + seconds(response.expires_in, 60) * 1000,
    refreshExpiresAt: response.refresh_token ? now + seconds(response.refresh_expires_in, 1800) * 1000 : 0,
  };
}

/** True when the access token is missing or expires within `skewMs`. */
export function needsRefresh(tokens, now, skewMs = 30_000) {
  return !tokens?.accessToken || tokens.expiresAt - skewMs <= now;
}

/** The claims of a JWT, without checking its signature (the API does that); null if malformed. */
export function decodeClaims(jwt) {
  try {
    const part = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      Array.from(atob(part.padEnd(part.length + ((4 - (part.length % 4)) % 4), '=')))
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/** Where to return after sign-in: only a path in this app, never another site. */
export function safeReturnPath(path) {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') ? path : '/';
}
