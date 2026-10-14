"""Validates the signed-in user's access token (issued by Keycloak) and reads its tenant."""

import threading
import time
from functools import lru_cache
from uuid import UUID

import jwt

ALGORITHMS = ["RS256", "RS384", "RS512", "PS256", "ES256"]


class InvalidToken(Exception):
    """The token is missing, expired, forged, for another audience, or has no tenant."""


class TokenVerifier:
    def __init__(self, issuer: str, audience: str, keys):
        """``keys`` resolves a token to its signing key (a PyJWKClient, or a stand-in in tests)."""
        self._issuer = issuer
        self._audience = audience
        self._keys = keys

    def tenant(self, token: str) -> UUID:
        try:
            key = self._keys.get_signing_key_from_jwt(token).key
            claims = jwt.decode(
                token,
                key,
                algorithms=ALGORITHMS,
                audience=self._audience,
                issuer=self._issuer,
                leeway=30,
                options={"require": ["exp", "iss", "sub", "aud"]},
            )
        except (jwt.PyJWTError, ValueError, KeyError) as exc:
            raise InvalidToken(str(exc)) from exc
        try:
            return UUID(str(claims["tenant_id"]))
        except (KeyError, ValueError) as exc:
            raise InvalidToken("token has no valid tenant_id claim") from exc


class RealmKeys:
    """Keycloak's signing keys, cached. An unknown key id (Keycloak rotated its key) triggers a fresh
    download at most once a minute, so junk tokens cannot make the service hammer Keycloak."""

    def __init__(self, jwks_uri: str, min_refresh_seconds: float = 60, client=None):
        self._client = client or jwt.PyJWKClient(jwks_uri, cache_keys=False, cache_jwk_set=True, lifespan=3600)
        self._min_refresh = min_refresh_seconds
        self._last_refresh = float("-inf")
        self._lock = threading.Lock()

    def get_signing_key_from_jwt(self, token: str):
        kid = jwt.get_unverified_header(token).get("kid")
        key = self._find(self._client.get_signing_keys(), kid)
        if key is not None:
            return key
        with self._lock:
            if time.monotonic() - self._last_refresh < self._min_refresh:
                raise jwt.PyJWKClientError(f"Unknown signing key {kid!r}")
            self._last_refresh = time.monotonic()
        key = self._find(self._client.get_signing_keys(refresh=True), kid)
        if key is None:
            raise jwt.PyJWKClientError(f"Unknown signing key {kid!r}")
        return key

    @staticmethod
    def _find(keys, kid):
        return next((k for k in keys if k.key_id == kid), None)


@lru_cache
def verifier_for(issuer: str, audience: str, jwks_uri: str) -> TokenVerifier:
    return TokenVerifier(issuer, audience, RealmKeys(jwks_uri))
