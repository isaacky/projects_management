from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# Docker Swarm mounts secrets here, one file per setting (for example /run/secrets/PMP_DATABASE_URL).
_SECRETS = "/run/secrets"


class Settings(BaseSettings):
    """Read from environment variables prefixed PMP_ (for example PMP_DATABASE_URL), or secret files."""

    model_config = SettingsConfigDict(
        env_prefix="PMP_",
        env_file=".env",
        extra="ignore",
        secrets_dir=_SECRETS if Path(_SECRETS).is_dir() else None,
    )

    # Read-only connection as the pmp_analytics role. Row-level security limits each request to
    # the caller's tenant (the service sets app.tenant_id on the connection).
    database_url: str = "postgresql+psycopg://pmp:pmp@localhost:5432/pmp"

    # Development only: take the tenant from the X-Tenant-ID header, as the backend's dev profile
    # does. Must stay false on servers, where the tenant comes from the signed-in user's token.
    allow_tenant_header: bool = False

    # Keycloak, as for the backend: the issuer browsers see, the signing keys (an internal address
    # is fine), and the audience tokens must be issued for. Blank issuer = tokens not accepted.
    oidc_issuer_uri: str = ""
    oidc_jwks_uri: str = ""
    oidc_audience: str = "pmp-api"

    # Currency used for portfolio totals.
    reporting_currency: str = "KES"

    @property
    def jwks_uri(self) -> str:
        if self.oidc_jwks_uri:
            return self.oidc_jwks_uri
        return self.oidc_issuer_uri.rstrip("/") + "/protocol/openid-connect/certs"


@lru_cache
def get_settings() -> Settings:
    return Settings()
