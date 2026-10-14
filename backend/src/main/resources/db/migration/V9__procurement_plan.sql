-- V9: annual procurement plan (PPADA 2015 s.53).
--
-- One plan per financial year. Procurement opens it and adds the departments, each with a head
-- who enters the department's planned purchases and submits them. Procurement consolidates and
-- the head of procurement submits the plan to the accounting officer, who approves it or returns
-- it. An approved plan can be reopened for revision; each approval is kept as a revision.
--
-- A requisition submitted while an approved plan covers the date must either name a plan line
-- (and fit within its remaining balance) or give the reason it is unplanned, in which case the
-- accounting officer is added to its approval chain.

CREATE TABLE procurement.plans (
    id               uuid PRIMARY KEY,
    tenant_id        uuid          NOT NULL REFERENCES platform.tenants (id),
    financial_year   varchar(9)    NOT NULL,                 -- e.g. 2026/27
    fy_start         date          NOT NULL CHECK (EXTRACT(DAY FROM fy_start) = 1),
    fy_end           date          NOT NULL,
    currency         varchar(3)    NOT NULL DEFAULT 'KES',
    status           varchar(12)   NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED')),
    -- Number of approvals so far (0 = never approved).
    revision         integer       NOT NULL DEFAULT 0,
    next_line_no     integer       NOT NULL DEFAULT 1,
    -- Share of the plan to reserve for AGPO groups (PPADA s.157: at least 30%).
    agpo_target_pct  numeric(5, 2) NOT NULL DEFAULT 30 CHECK (agpo_target_pct BETWEEN 0 AND 100),
    revision_reason  varchar(2000),
    return_note      varchar(2000),
    submitted_by     uuid          REFERENCES platform.users (id),
    submitted_at     timestamptz,
    created_by       uuid          NOT NULL REFERENCES platform.users (id),
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    CHECK (fy_end > fy_start),
    UNIQUE (tenant_id, fy_start)
);

CREATE TABLE procurement.plan_departments (
    id             uuid PRIMARY KEY,
    tenant_id      uuid          NOT NULL REFERENCES platform.tenants (id),
    plan_id        uuid          NOT NULL REFERENCES procurement.plans (id),
    name           varchar(120)  NOT NULL,
    head_id        uuid          NOT NULL REFERENCES platform.users (id),
    ceiling_minor  bigint        CHECK (ceiling_minor >= 0),
    status         varchar(12)   NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED')),
    return_note    varchar(2000),
    submitted_by   uuid          REFERENCES platform.users (id),
    submitted_at   timestamptz,
    updated_at     timestamptz   NOT NULL,
    version        bigint        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX plan_departments_name_uq ON procurement.plan_departments (plan_id, lower(name));

CREATE TABLE procurement.plan_lines (
    id                       uuid PRIMARY KEY,
    tenant_id                uuid           NOT NULL REFERENCES platform.tenants (id),
    plan_id                  uuid           NOT NULL REFERENCES procurement.plans (id),
    department_id            uuid           NOT NULL REFERENCES procurement.plan_departments (id),
    line_no                  integer        NOT NULL CHECK (line_no >= 1),
    description              varchar(500)   NOT NULL,
    category                 varchar(16)    NOT NULL
                             CHECK (category IN ('GOODS', 'WORKS', 'NON_CONSULTING', 'CONSULTANCY')),
    quantity                 numeric(14, 3) NOT NULL CHECK (quantity > 0),
    unit                     varchar(32)    NOT NULL,
    estimate_minor           bigint         NOT NULL CHECK (estimate_minor >= 0),
    method_code              varchar(40)    NOT NULL,
    -- Quarter of the financial year in which procurement should start.
    quarter                  integer        NOT NULL CHECK (quarter BETWEEN 1 AND 4),
    source_of_funds          varchar(120),
    budget_code              varchar(60),
    reserved_group           varchar(8)     CHECK (reserved_group IN ('YOUTH', 'WOMEN', 'PWD', 'AGPO')),
    project_id               uuid           REFERENCES portfolio.projects (id),
    -- Set when the accounting officer approves the plan: what requisitions are measured against.
    approved_estimate_minor  bigint         CHECK (approved_estimate_minor >= 0),
    approved_revision        integer,
    cancelled_at             timestamptz,
    cancel_note              varchar(2000),
    created_by               uuid           NOT NULL REFERENCES platform.users (id),
    created_at               timestamptz    NOT NULL,
    updated_at               timestamptz    NOT NULL,
    version                  bigint         NOT NULL DEFAULT 0,
    UNIQUE (plan_id, line_no)
);
CREATE INDEX plan_lines_department_idx ON procurement.plan_lines (department_id);

CREATE TABLE procurement.plan_revisions (
    id            uuid PRIMARY KEY,
    tenant_id     uuid          NOT NULL REFERENCES platform.tenants (id),
    plan_id       uuid          NOT NULL REFERENCES procurement.plans (id),
    revision      integer       NOT NULL CHECK (revision >= 1),
    approved_by   uuid          NOT NULL REFERENCES platform.users (id),
    approved_at   timestamptz   NOT NULL,
    total_minor   bigint        NOT NULL,
    line_count    integer       NOT NULL,
    reason        varchar(2000),
    note          varchar(2000),
    updated_at    timestamptz   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0,
    UNIQUE (plan_id, revision)
);

ALTER TABLE procurement.requisitions
    ADD COLUMN plan_line_id      uuid REFERENCES procurement.plan_lines (id),
    ADD COLUMN unplanned_reason  varchar(2000),
    -- Decided at submission: PLANNED (names a plan line), UNPLANNED (an approved plan covered the
    -- date but no line was named) or NO_PLAN (no approved plan for that financial year).
    ADD COLUMN plan_status       varchar(10) CHECK (plan_status IN ('PLANNED', 'UNPLANNED', 'NO_PLAN'));
CREATE INDEX requisitions_plan_line_idx ON procurement.requisitions (plan_line_id);
