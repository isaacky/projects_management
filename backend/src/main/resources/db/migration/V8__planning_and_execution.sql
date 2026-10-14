-- V8: planning (project schedule) and execution (progress, risks, issues, status reports).
--
-- The schedule is a work breakdown of activities with finish-to-start or start-to-start
-- dependencies, scheduled in calendar days by the critical path method in the application.
-- A baseline freezes the planned dates; progress is recorded on the activities and a
-- project-level snapshot is kept for each day progress changes (the S-curve).

CREATE SCHEMA IF NOT EXISTS planning;
CREATE SCHEMA IF NOT EXISTS execution;

-- ---------------------------------------------------------------------------
-- Planning
-- ---------------------------------------------------------------------------
CREATE TABLE planning.schedules (
    project_id         uuid PRIMARY KEY REFERENCES portfolio.projects (id),
    tenant_id          uuid          NOT NULL REFERENCES platform.tenants (id),
    start_date         date          NOT NULL,
    baseline_set_at    timestamptz,
    baseline_set_by    uuid          REFERENCES platform.users (id),
    baseline_finish    date,
    baseline_note      varchar(1000),
    baseline_count     integer       NOT NULL DEFAULT 0,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL DEFAULT 0
);

CREATE TABLE planning.activities (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id          uuid          NOT NULL REFERENCES planning.schedules (project_id) ON DELETE CASCADE,
    parent_id           uuid          REFERENCES planning.activities (id),
    sort_order          integer       NOT NULL,
    name                varchar(200)  NOT NULL,
    description         varchar(2000),
    duration_days       integer       NOT NULL DEFAULT 0 CHECK (duration_days BETWEEN 0 AND 3650),
    -- Start no earlier than this date (NULL = the schedule start).
    earliest_start      date,
    -- Share of the project's progress; NULL = its duration.
    weight              numeric(10, 2) CHECK (weight >= 0),
    owner_id            uuid          REFERENCES platform.users (id),
    baseline_start      date,
    baseline_finish     date,
    actual_start        date,
    actual_finish       date,
    percent_complete    numeric(5, 2) NOT NULL DEFAULT 0 CHECK (percent_complete BETWEEN 0 AND 100),
    progress_note       varchar(1000),
    progress_updated_at timestamptz,
    progress_updated_by uuid          REFERENCES platform.users (id),
    updated_at          timestamptz   NOT NULL,
    version             bigint        NOT NULL DEFAULT 0,
    CHECK (actual_finish IS NULL OR (actual_start IS NOT NULL AND actual_finish >= actual_start))
);
CREATE INDEX activities_project_idx ON planning.activities (project_id, sort_order);

CREATE TABLE planning.dependencies (
    id               uuid PRIMARY KEY,
    project_id       uuid          NOT NULL REFERENCES planning.schedules (project_id) ON DELETE CASCADE,
    predecessor_id   uuid          NOT NULL REFERENCES planning.activities (id) ON DELETE CASCADE,
    successor_id     uuid          NOT NULL REFERENCES planning.activities (id) ON DELETE CASCADE,
    type             varchar(2)    NOT NULL CHECK (type IN ('FS', 'SS')),
    lag_days         integer       NOT NULL DEFAULT 0 CHECK (lag_days BETWEEN -365 AND 365),
    version          bigint        NOT NULL DEFAULT 0,
    UNIQUE (predecessor_id, successor_id),
    CHECK (predecessor_id <> successor_id)
);
CREATE INDEX dependencies_project_idx ON planning.dependencies (project_id);

-- ---------------------------------------------------------------------------
-- Progress history (one row per project per day progress changed): the S-curve
-- ---------------------------------------------------------------------------
CREATE TABLE planning.progress_snapshots (
    id            uuid PRIMARY KEY,
    tenant_id     uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id    uuid          NOT NULL REFERENCES portfolio.projects (id),
    as_of         date          NOT NULL,
    planned_pct   numeric(5, 2) NOT NULL,
    earned_pct    numeric(5, 2) NOT NULL,
    recorded_by   uuid          NOT NULL REFERENCES platform.users (id),
    recorded_at   timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0,
    UNIQUE (project_id, as_of)
);

-- ---------------------------------------------------------------------------
-- Execution
-- ---------------------------------------------------------------------------

CREATE TABLE execution.risks (
    id            uuid PRIMARY KEY,
    tenant_id     uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id    uuid          NOT NULL REFERENCES portfolio.projects (id),
    number        integer       NOT NULL,
    title         varchar(200)  NOT NULL,
    description   varchar(2000),
    category      varchar(24)   NOT NULL
                  CHECK (category IN ('TECHNICAL', 'COMMERCIAL', 'SCHEDULE', 'SAFETY', 'ENVIRONMENTAL',
                                      'WAYLEAVE', 'REGULATORY', 'STAKEHOLDER', 'SECURITY', 'OTHER')),
    probability   integer       NOT NULL CHECK (probability BETWEEN 1 AND 5),
    impact        integer       NOT NULL CHECK (impact BETWEEN 1 AND 5),
    response      varchar(12)   NOT NULL CHECK (response IN ('AVOID', 'MITIGATE', 'TRANSFER', 'ACCEPT')),
    mitigation    varchar(4000),
    owner_id      uuid          REFERENCES platform.users (id),
    due_date      date,
    status        varchar(12)   NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    closed_note   varchar(2000),
    closed_at     timestamptz,
    created_by    uuid          NOT NULL REFERENCES platform.users (id),
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0,
    UNIQUE (project_id, number)
);

CREATE TABLE execution.issues (
    id            uuid PRIMARY KEY,
    tenant_id     uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id    uuid          NOT NULL REFERENCES portfolio.projects (id),
    number        integer       NOT NULL,
    title         varchar(200)  NOT NULL,
    description   varchar(2000),
    priority      varchar(12)   NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status        varchar(12)   NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    owner_id      uuid          REFERENCES platform.users (id),
    due_date      date,
    resolution    varchar(2000),
    risk_id       uuid          REFERENCES execution.risks (id),
    created_by    uuid          NOT NULL REFERENCES platform.users (id),
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0,
    UNIQUE (project_id, number)
);

CREATE TABLE execution.status_reports (
    id               uuid PRIMARY KEY,
    tenant_id        uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id       uuid          NOT NULL REFERENCES portfolio.projects (id),
    period_end       date          NOT NULL,
    status           varchar(12)   NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'REVIEWED')),
    rag_overall      varchar(5)    NOT NULL CHECK (rag_overall IN ('GREEN', 'AMBER', 'RED')),
    rag_schedule     varchar(5)    NOT NULL CHECK (rag_schedule IN ('GREEN', 'AMBER', 'RED')),
    rag_cost         varchar(5)    NOT NULL CHECK (rag_cost IN ('GREEN', 'AMBER', 'RED')),
    rag_scope        varchar(5)    NOT NULL CHECK (rag_scope IN ('GREEN', 'AMBER', 'RED')),
    rag_risk         varchar(5)    NOT NULL CHECK (rag_risk IN ('GREEN', 'AMBER', 'RED')),
    summary          varchar(4000) NOT NULL,
    achievements     varchar(4000),
    next_steps       varchar(4000),
    escalations      varchar(4000),
    -- Figures frozen when the report is submitted.
    planned_pct      numeric(5, 2),
    earned_pct       numeric(5, 2),
    spi              numeric(8, 3),
    cpi              numeric(8, 3),
    baseline_finish  date,
    forecast_finish  date,
    budget_minor     bigint,
    committed_minor  bigint,
    certified_minor  bigint,
    open_risks       integer,
    high_risks       integer,
    critical_risks   integer,
    slip_days        integer,
    open_issues      integer,
    submitted_by     uuid          REFERENCES platform.users (id),
    submitted_at     timestamptz,
    reviewed_by      uuid          REFERENCES platform.users (id),
    reviewed_at      timestamptz,
    review_comment   varchar(2000),
    created_by       uuid          NOT NULL REFERENCES platform.users (id),
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    UNIQUE (project_id, period_end)
);
