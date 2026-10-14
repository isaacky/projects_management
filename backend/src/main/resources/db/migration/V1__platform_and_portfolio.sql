-- V1: platform core (tenants, users, audit log) and the portfolio spine (projects).
-- Each bounded context owns one schema. Flyway creates the schemas listed in
-- spring.flyway.schemas; the statements below are idempotent for safety.

CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS portfolio;

-- ---------------------------------------------------------------------------
-- Tenants and users
-- ---------------------------------------------------------------------------
CREATE TABLE platform.tenants (
    id          uuid PRIMARY KEY,
    slug        varchar(64)  NOT NULL UNIQUE,
    name        varchar(200) NOT NULL,
    status      varchar(16)  NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE platform.users (
    id                uuid PRIMARY KEY,
    tenant_id         uuid         NOT NULL REFERENCES platform.tenants (id),
    external_subject  varchar(255),            -- "sub" claim from the identity provider
    email             varchar(320) NOT NULL,
    display_name      varchar(200) NOT NULL,
    active            boolean      NOT NULL DEFAULT true,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email),
    UNIQUE (tenant_id, external_subject)
);

-- ---------------------------------------------------------------------------
-- Append-only, hash-chained audit log
-- ---------------------------------------------------------------------------
CREATE TABLE platform.audit_events (
    id           uuid PRIMARY KEY,
    seq          bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    tenant_id    uuid         NOT NULL REFERENCES platform.tenants (id),
    occurred_at  timestamptz  NOT NULL,
    actor        varchar(255) NOT NULL,
    action       varchar(100) NOT NULL,
    entity_type  varchar(100) NOT NULL,
    entity_id    uuid         NOT NULL,
    payload      varchar      NOT NULL,
    prev_hash    varchar(64),
    hash         varchar(64)  NOT NULL
);

CREATE INDEX audit_events_tenant_seq_idx ON platform.audit_events (tenant_id, seq);
CREATE INDEX audit_events_entity_idx ON platform.audit_events (tenant_id, entity_id, seq);

-- Refuse any change to existing audit rows, whoever the database user is.
CREATE FUNCTION platform.audit_events_immutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$;

CREATE TRIGGER audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON platform.audit_events
    FOR EACH ROW EXECUTE FUNCTION platform.audit_events_immutable();

-- ---------------------------------------------------------------------------
-- Projects
-- ---------------------------------------------------------------------------
CREATE TABLE portfolio.projects (
    id            uuid PRIMARY KEY,
    tenant_id     uuid          NOT NULL REFERENCES platform.tenants (id),
    code          varchar(32)   NOT NULL,
    name          varchar(200)  NOT NULL,
    description   varchar(4000),
    sponsor       varchar(200),
    phase         varchar(32)   NOT NULL
                  CHECK (phase IN ('INITIATION', 'PROCUREMENT', 'PLANNING',
                                   'EXECUTION', 'CLOSEOUT', 'IMPLEMENTATION')),
    status        varchar(16)   NOT NULL
                  CHECK (status IN ('ACTIVE', 'ON_HOLD', 'CANCELLED', 'CLOSED')),
    budget_minor  bigint        NOT NULL CHECK (budget_minor >= 0),
    currency      varchar(3)    NOT NULL,
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE INDEX projects_tenant_phase_idx ON portfolio.projects (tenant_id, phase);

-- Row-level security is added in a later migration together with a separate,
-- non-owner application role, so policies cannot be bypassed by the table owner.
