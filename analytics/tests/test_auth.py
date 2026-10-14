import time
from uuid import UUID

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from app.auth import InvalidToken, RealmKeys, TokenVerifier

ISSUER = "https://pmp.example/auth/realms/pmp"
TENANT = "01920000-0000-7000-8000-000000000001"
KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)
OTHER_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)


class _Key:
    def __init__(self, key):
        self.key = key


class FakeKeys:
    """Stands in for PyJWKClient: always the realm's public key."""

    def get_signing_key_from_jwt(self, token):
        return _Key(KEY.public_key())


def token(signing_key=KEY, **overrides):
    claims = {
        "iss": ISSUER,
        "aud": ["pmp-api", "account"],
        "sub": "user-1",
        "exp": int(time.time()) + 300,
        "tenant_id": TENANT,
    }
    claims.update(overrides)
    claims = {k: v for k, v in claims.items() if v is not None}
    return jwt.encode(claims, signing_key, algorithm="RS256")


VERIFIER = TokenVerifier(ISSUER, "pmp-api", FakeKeys())


def test_valid_token_gives_its_tenant():
    assert VERIFIER.tenant(token()) == UUID(TENANT)


@pytest.mark.parametrize(
    "bad",
    [
        {"signing_key": OTHER_KEY},
        {"exp": int(time.time()) - 120},
        {"iss": "https://elsewhere/realms/pmp"},
        {"aud": "account"},
        {"tenant_id": None},
        {"tenant_id": "not-a-uuid"},
    ],
)
def test_bad_tokens_are_rejected(bad):
    with pytest.raises(InvalidToken):
        VERIFIER.tenant(token(**bad))


def test_garbage_is_rejected():
    with pytest.raises(InvalidToken):
        VERIFIER.tenant("not.a.token")


class FakeJwksClient:
    """Counts downloads of the key set."""

    def __init__(self):
        self.downloads = 0

    def get_signing_keys(self, refresh=False):
        if refresh:
            self.downloads += 1
        return [jwt.PyJWK.from_dict({**jwt.algorithms.RSAAlgorithm.to_jwk(KEY.public_key(), as_dict=True),
                                     "kid": "k1", "alg": "RS256", "use": "sig"})]


def signed(kid):
    return jwt.encode({"iss": ISSUER, "aud": "pmp-api", "sub": "u", "exp": int(time.time()) + 300,
                       "tenant_id": TENANT}, KEY, algorithm="RS256", headers={"kid": kid})


def test_known_key_needs_no_download():
    client = FakeJwksClient()
    verifier = TokenVerifier(ISSUER, "pmp-api", RealmKeys("unused", client=client))
    assert verifier.tenant(signed("k1")) == UUID(TENANT)
    assert client.downloads == 0


def test_unknown_keys_refresh_at_most_once_a_minute():
    client = FakeJwksClient()
    verifier = TokenVerifier(ISSUER, "pmp-api", RealmKeys("unused", client=client))
    for _ in range(5):
        with pytest.raises(InvalidToken):
            verifier.tenant(signed("forged"))
    assert client.downloads == 1
