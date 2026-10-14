-- V11: implementation and benefits realisation.
--
-- Each project keeps a benefits register: what the investment should achieve, measured by a KPI
-- from a baseline to a target by a date, with an owner who records measurements on a schedule.
-- Post-implementation reviews (typically 6 and 12 months after handover) assess each benefit and
-- are frozen when completed. Benefits outlive the project: they can be measured after it closes.

CREATE SCHEMA IF NOT EXISTS benefits;

CREATE TABLE benefits.benefits (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid           NOT NULL REFERENCES platform.tenants (id),
    project_id         uuid           NOT NULL REFERENCES portfolio.projects (id),
    number             integer        NOT NULL CHECK (number >= 1),
    title              varchar(200)   NOT NULL,
    description        varchar(2000),
    category           varchar(16)    NOT NULL
                       CHECK (category IN ('FINANCIAL', 'OPERATIONAL', 'CUSTOMER', 'SAFETY', 'ENVIRONMENTAL',
                                           'STRATEGIC')),
    -- The business-case objective the benefit serves.
    objective          varchar(500),
    owner_id           uuid           NOT NULL REFERENCES platform.users (id),
    measure            varchar(200)   NOT NULL,             -- the KPI, e.g. "Customers connected"
    unit               varchar(32)    NOT NULL,             -- e.g. customers, minutes, %, KES
    direction          varchar(8)     NOT NULL CHECK (direction IN ('INCREASE', 'DECREASE')),
    baseline_value     numeric(18, 4) NOT NULL,
    baseline_date      date           NOT NULL,
    target_value       numeric(18, 4) NOT NULL,
    target_date        date           NOT NULL,
    frequency          varchar(12)    NOT NULL
                       CHECK (frequency IN ('MONTHLY', 'QUARTERLY', 'SEMI_ANNUAL', 'ANNUAL')),
    -- Optional yearly value in money once the target is reached (for the portfolio roll-up).
    annual_value_minor bigint         CHECK (annual_value_minor >= 0),
    status             varchar(14)    NOT NULL
                       CHECK (status IN ('ACTIVE', 'ACHIEVED', 'NOT_ACHIEVED', 'CANCELLED')),
    closed_note        varchar(2000),
    closed_by          uuid           REFERENCES platform.users (id),
    closed_at          timestamptz,
    -- Last due date a reminder was sent for, so the daily job sends one per due date.
    reminded_due       date,
    created_by         uuid           NOT NULL REFERENCES platform.users (id),
    created_at         timestamptz    NOT NULL,
    updated_at         timestamptz    NOT NULL,
    version            bigint         NOT NULL DEFAULT 0,
    CHECK (target_value <> baseline_value),
    CHECK (target_date > baseline_date),
    UNIQUE (project_id, number)
);
CREATE INDEX benefits_tenant_status_idx ON benefits.benefits (tenant_id, status);

CREATE TABLE benefits.measurements (
    id           uuid PRIMARY KEY,
    tenant_id    uuid           NOT NULL REFERENCES platform.tenants (id),
    benefit_id   uuid           NOT NULL REFERENCES benefits.benefits (id) ON DELETE CASCADE,
    measured_on  date           NOT NULL,
    value        numeric(18, 4) NOT NULL,
    source       varchar(200),                              -- where the figure comes from
    note         varchar(1000),
    recorded_by  uuid           NOT NULL REFERENCES platform.users (id),
    recorded_at  timestamptz    NOT NULL,
    updated_at   timestamptz    NOT NULL,
    version      bigint         NOT NULL DEFAULT 0,
    UNIQUE (benefit_id, measured_on)
);

CREATE TABLE benefits.reviews (
    id               uuid PRIMARY KEY,
    tenant_id        uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id       uuid          NOT NULL REFERENCES portfolio.projects (id),
    title            varchar(200)  NOT NULL,
    due_date         date          NOT NULL,
    lead_id          uuid          NOT NULL REFERENCES platform.users (id),
    status           varchar(10)   NOT NULL CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELLED')),
    conducted_on     date,
    objectives_met   varchar(8)    CHECK (objectives_met IN ('YES', 'PARTLY', 'NO')),
    summary          varchar(4000),
    recommendations  varchar(4000),
    cancel_note      varchar(2000),
    completed_by     uuid          REFERENCES platform.users (id),
    completed_at     timestamptz,
    reminded         boolean       NOT NULL DEFAULT false,
    created_by       uuid          NOT NULL REFERENCES platform.users (id),
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0
);
CREATE INDEX reviews_project_idx ON benefits.reviews (project_id, due_date);

-- Each benefit's position as assessed (and frozen) in a completed review.
CREATE TABLE benefits.review_assessments (
    id            uuid PRIMARY KEY,
    review_id     uuid           NOT NULL REFERENCES benefits.reviews (id) ON DELETE CASCADE,
    benefit_id    uuid           NOT NULL REFERENCES benefits.benefits (id),
    -- Frozen when the review is completed, so later edits to the benefit do not change it.
    baseline_value numeric(18, 4),
    target_value  numeric(18, 4),
    actual_value  numeric(18, 4),
    measured_on   date,
    progress_pct  numeric(12, 2),
    rating        varchar(9)     NOT NULL CHECK (rating IN ('ON_TRACK', 'AT_RISK', 'OFF_TRACK', 'ACHIEVED')),
    comment       varchar(2000),
    version       bigint         NOT NULL DEFAULT 0,
    UNIQUE (review_id, benefit_id)
);
