from uuid import UUID

from fastapi.testclient import TestClient

from app.config import Settings, get_settings
from app.main import app
from app.repository import get_repository

TENANT = "01920000-0000-7000-8000-000000000001"


class FakeRepository:
    def __init__(self):
        self.asked_for = None

    def projects(self, tenant_id: UUID) -> list[dict]:
        self.asked_for = tenant_id
        return [
            {"code": "A", "phase": "PLANNING", "status": "ACTIVE",
             "budget_minor": 1000, "currency": "KES"},
        ]

    def ping(self) -> None:
        pass


def client(allow_header: bool) -> tuple[TestClient, FakeRepository]:
    repo = FakeRepository()
    app.dependency_overrides[get_repository] = lambda: repo
    app.dependency_overrides[get_settings] = lambda: Settings(allow_tenant_header=allow_header)
    return TestClient(app), repo


def teardown_function():
    app.dependency_overrides.clear()


def test_health():
    c, _ = client(allow_header=True)
    assert c.get("/health").json() == {"status": "UP"}


def test_summary_is_scoped_to_the_tenant():
    c, repo = client(allow_header=True)
    res = c.get("/portfolio/summary", headers={"X-Tenant-ID": TENANT})
    assert res.status_code == 200
    assert res.json()["active_count"] == 1
    assert repo.asked_for == UUID(TENANT)


def test_missing_or_bad_tenant_is_rejected():
    c, _ = client(allow_header=True)
    assert c.get("/portfolio/summary").status_code == 400
    assert c.get("/portfolio/summary", headers={"X-Tenant-ID": "nope"}).status_code == 400


def test_header_is_ignored_outside_development():
    c, _ = client(allow_header=False)
    res = c.get("/portfolio/summary", headers={"X-Tenant-ID": TENANT})
    assert res.status_code == 401


def test_bearer_token_sets_the_tenant(monkeypatch):
    from test_auth import ISSUER, VERIFIER, token

    repo = FakeRepository()
    app.dependency_overrides[get_repository] = lambda: repo
    app.dependency_overrides[get_settings] = lambda: Settings(
        allow_tenant_header=False, oidc_issuer_uri=ISSUER
    )
    monkeypatch.setattr("app.main.verifier_for", lambda *a: VERIFIER)
    c = TestClient(app)
    res = c.get("/portfolio/summary", headers={"Authorization": "Bearer " + token()})
    assert res.status_code == 200
    assert repo.asked_for == UUID(TENANT)
    bad = c.get("/portfolio/summary", headers={"Authorization": "Bearer nonsense"})
    assert bad.status_code == 401


def test_token_without_sign_in_configured_says_so():
    c, _ = client(allow_header=True)
    res = c.get("/portfolio/summary", headers={"Authorization": "Bearer abc", "X-Tenant-ID": TENANT})
    assert res.status_code == 401
    assert "PMP_OIDC_ISSUER_URI" in res.json()["detail"]
