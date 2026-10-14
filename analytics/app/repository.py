"""Read-only data access. Every query is scoped to one tenant, by its WHERE clause and by the
database: the connection's app.tenant_id setting, which row-level security checks."""

from functools import lru_cache
from uuid import UUID

from sqlalchemy import Engine, create_engine, text

from app.config import get_settings

SET_TENANT = text("SELECT set_config('app.tenant_id', :tenant_id, true)")  # this transaction only

PROJECTS_FOR_TENANT = text(
    """
    SELECT code, phase, status, budget_minor, currency
    FROM portfolio.projects
    WHERE tenant_id = :tenant_id
    """
)


@lru_cache
def get_engine() -> Engine:
    return create_engine(get_settings().database_url, pool_pre_ping=True, pool_size=5)


class ProjectRepository:
    def __init__(self, engine: Engine):
        self._engine = engine

    def projects(self, tenant_id: UUID) -> list[dict]:
        with self._engine.connect() as conn:
            conn.execute(SET_TENANT, {"tenant_id": str(tenant_id)})
            result = conn.execute(PROJECTS_FOR_TENANT, {"tenant_id": tenant_id})
            return [dict(row._mapping) for row in result]

    def ping(self) -> None:
        with self._engine.connect() as conn:
            conn.execute(text("SELECT 1"))


def get_repository() -> ProjectRepository:
    return ProjectRepository(get_engine())
