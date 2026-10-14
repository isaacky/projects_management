import { describe, expect, it } from 'vitest';
import {
  authorizeUrl,
  base64Url,
  codeChallenge,
  decodeClaims,
  logoutUrl,
  needsRefresh,
  randomString,
  safeReturnPath,
  tokensFrom,
} from './oidc.js';

describe('oidc helpers', () => {
  it('encodes base64url without padding', () => {
    expect(base64Url(new Uint8Array([251, 255, 191]))).toBe('-_-_');
    expect(base64Url(new Uint8Array([1]))).toBe('AQ');
  });

  it('makes random strings of the expected length', () => {
    const fake = { getRandomValues: (b) => b.fill(7) };
    expect(randomString(fake, 32)).toHaveLength(43);
    expect(randomString(fake, 48)).toHaveLength(64);
  });

  it('computes the S256 challenge (SHA-256, base64url)', async () => {
    const challenge = await codeChallenge(globalThis.crypto, 'dBjftJeZ4CVP-mJ92K9a7EpNkKwKjL5QqvIzMs3lKrE');
    expect(challenge).toBe('1L91aaepGcGMOKXctoKgtFVVwr-Fs74EVWy5Guu6tFM'); // checked with Python hashlib
  });

  it('builds the sign-in and sign-out addresses', () => {
    const url = new URL(
      authorizeUrl('https://pmp.example/auth/realms/pmp/protocol/openid-connect/auth', {
        clientId: 'pmp-web',
        redirectUri: 'https://pmp.example/',
        state: 's',
        nonce: 'n',
        challenge: 'c',
      }),
    );
    expect(url.searchParams.get('response_type')).toBe('code');
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    expect(url.searchParams.get('redirect_uri')).toBe('https://pmp.example/');
    expect(url.searchParams.get('scope')).toBe('openid profile email');
    const out = new URL(logoutUrl('https://kc/logout', { clientId: 'pmp-web', idToken: 'abc', redirectUri: 'https://pmp.example/' }));
    expect(out.searchParams.get('id_token_hint')).toBe('abc');
    expect(out.searchParams.get('post_logout_redirect_uri')).toBe('https://pmp.example/');
  });

  it('keeps tokens with absolute expiry times', () => {
    const t = tokensFrom({ access_token: 'a', refresh_token: 'r', id_token: 'i', expires_in: 300, refresh_expires_in: 1800 }, 1000);
    expect(t).toEqual({ accessToken: 'a', refreshToken: 'r', idToken: 'i', expiresAt: 301000, refreshExpiresAt: 1801000 });
    expect(needsRefresh(t, 200000)).toBe(false);
    expect(needsRefresh(t, 280000)).toBe(true);
    expect(needsRefresh(null, 0)).toBe(true);
    expect(() => tokensFrom({}, 0)).toThrow();
  });

  it('reads token claims, including non-ASCII names', () => {
    const payload = base64Url(new TextEncoder().encode(JSON.stringify({ email: 'a@b.co', name: 'Wanjirū' })));
    expect(decodeClaims(`x.${payload}.y`)).toEqual({ email: 'a@b.co', name: 'Wanjirū' });
    expect(decodeClaims('garbage')).toBeNull();
  });

  it('only returns to paths inside the app', () => {
    expect(safeReturnPath('/reports/payments?x=1')).toBe('/reports/payments?x=1');
    expect(safeReturnPath('//evil.example')).toBe('/');
    expect(safeReturnPath('https://evil.example')).toBe('/');
    expect(safeReturnPath(undefined)).toBe('/');
  });
});
