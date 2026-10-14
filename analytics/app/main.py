from typing import Annotated
from uuid import UUID

from fastapi import Depends, FastAPI, Header, HTTPException, status

from app.auth import InvalidToken, verifier_for
from app.config import Settings, get_settings
from app.repository import ProjectRepository, get_repository
from app.summary import portfolio_summary

app = FastAPI(
    title="PM Platform Analytics",
    version="0.1.0",
    description="Read-only portfolio analytics over the platform database.",
)


def current_tenant(
    settings: Annotated[Settings, Depends(get_settings)],
    authorization: Annotated[str | None, Header()] = None,
    x_tenant_id: Annotated[str | None, Header()] = None,
) -> UUID:
    if authorization and settings.oidc_issuer_uri:
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token:
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Bearer token required")
        verifier = verifier_for(settings.oidc_issuer_uri, settings.oidc_audience, settings.jwks_uri)
        try:
            return verifier.tenant(token.strip())
        except InvalidToken as exc:
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired token") from exc
    if authorization and not settings.oidc_issuer_uri:
        # The web app signed in through Keycloak, but this service was started without it.
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            "Analytics is not set up for sign-in: start it with PMP_OIDC_ISSUER_URI "
            "(locally: http://localhost:8180/realms/pmp)",
        )
    if not settings.allow_tenant_header:
        # Never trust an unsigned header outside development.
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Authentication required")
    if not x_tenant_id:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "X-Tenant-ID header required")
    try:
        return UUID(x_tenant_id)
    except ValueError as exc:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "X-Tenant-ID must be a UUID") from exc


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}


@app.get("/health/ready")
def ready(repo: Annotated[ProjectRepository, Depends(get_repository)]) -> dict:
    try:
        repo.ping()
    except Exception as exc:  # noqa: BLE001 - any database failure means not ready
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Database unavailable") from exc
    return {"status": "UP"}


@app.get("/portfolio/summary")
def summary(
    tenant: Annotated[UUID, Depends(current_tenant)],
    repo: Annotated[ProjectRepository, Depends(get_repository)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> dict:
    return portfolio_summary(repo.projects(tenant), settings.reporting_currency)
