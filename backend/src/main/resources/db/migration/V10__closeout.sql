-- V10: closeout and handover.
--
-- When the works are complete the project team starts closeout: a completion checklist (copied
-- from the organisation's standard list), a defects register for the defects liability period,
-- lessons learned, and the handover of the finished assets to the unit that will operate them.
-- The receiving officer accepts the handover, which freezes the final figures and opens gate G4.
-- Once the defects liability period is over and no defects are open, the PMO closes the project.

CREATE SCHEMA IF NOT EXISTS closeout;

-- The organisation's standard completion checklist.
CREATE TABLE closeout.checklist_templates (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid          NOT NULL REFERENCES platform.tenants (id),
    sort_order         integer       NOT NULL,
    title              varchar(200)  NOT NULL,
    description        varchar(1000),
    responsible_role   varchar(64),
    evidence_required  boolean       NOT NULL DEFAULT false,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL DEFAULT 0
);
CREATE INDEX checklist_templates_tenant_idx ON closeout.checklist_templates (tenant_id, sort_order);

CREATE TABLE closeout.closeouts (
    project_id               uuid PRIMARY KEY REFERENCES portfolio.projects (id),
    tenant_id                uuid          NOT NULL REFERENCES platform.tenants (id),
    status                   varchar(20)   NOT NULL
                             CHECK (status IN ('OPEN', 'HANDOVER_SUBMITTED', 'HANDED_OVER', 'CLOSED')),
    started_by               uuid          NOT NULL REFERENCES platform.users (id),
    started_at               timestamptz   NOT NULL,
    -- Handover to the unit that will operate the assets.
    receiving_unit           varchar(120),
    receiving_officer_id     uuid          REFERENCES platform.users (id),
    handover_date            date,
    handover_note            varchar(4000),
    handover_submitted_by    uuid          REFERENCES platform.users (id),
    handover_submitted_at    timestamptz,
    handover_decided_by      uuid          REFERENCES platform.users (id),
    handover_decided_at      timestamptz,
    handover_decision_note   varchar(2000),
    -- Final figures, frozen when the handover is accepted.
    budget_minor             bigint,
    contract_value_minor     bigint,
    certified_minor          bigint,
    paid_minor               bigint,
    baseline_finish          date,
    actual_finish            date,
    earned_pct               numeric(5, 2),
    dlp_end                  date,
    closed_by                uuid          REFERENCES platform.users (id),
    closed_at                timestamptz,
    closure_note             varchar(2000),
    updated_at               timestamptz   NOT NULL,
    version                  bigint        NOT NULL DEFAULT 0
);

CREATE TABLE closeout.checklist_items (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id         uuid          NOT NULL REFERENCES closeout.closeouts (project_id),
    sort_order         integer       NOT NULL,
    title              varchar(200)  NOT NULL,
    description        varchar(1000),
    responsible_role   varchar(64),
    evidence_required  boolean       NOT NULL DEFAULT false,
    status             varchar(16)   NOT NULL CHECK (status IN ('OPEN', 'DONE', 'NOT_APPLICABLE')),
    note               varchar(2000),
    done_by            uuid          REFERENCES platform.users (id),
    done_at            timestamptz,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL DEFAULT 0
);
CREATE INDEX checklist_items_project_idx ON closeout.checklist_items (project_id, sort_order);

CREATE TABLE closeout.defects (
    id               uuid PRIMARY KEY,
    tenant_id        uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id       uuid          NOT NULL REFERENCES closeout.closeouts (project_id),
    number           integer       NOT NULL,
    contract_id      uuid          REFERENCES contracts.contracts (id),
    title            varchar(200)  NOT NULL,
    description      varchar(4000),
    location         varchar(200),
    severity         varchar(10)   NOT NULL CHECK (severity IN ('MINOR', 'MAJOR', 'CRITICAL')),
    status           varchar(10)   NOT NULL CHECK (status IN ('OPEN', 'RECTIFIED', 'CLOSED', 'WAIVED')),
    due_date         date,
    reported_by      uuid          NOT NULL REFERENCES platform.users (id),
    reported_at      timestamptz   NOT NULL,
    rectified_by     uuid          REFERENCES platform.users (id),
    rectified_at     timestamptz,
    rectified_note   varchar(2000),
    closed_by        uuid          REFERENCES platform.users (id),
    closed_at        timestamptz,
    closed_note      varchar(2000),
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    UNIQUE (project_id, number)
);
CREATE INDEX defects_contract_idx ON closeout.defects (contract_id) WHERE status IN ('OPEN', 'RECTIFIED');

CREATE TABLE closeout.handover_assets (
    id             uuid PRIMARY KEY,
    tenant_id      uuid           NOT NULL REFERENCES platform.tenants (id),
    project_id     uuid           NOT NULL REFERENCES closeout.closeouts (project_id),
    sort_order     integer        NOT NULL,
    description    varchar(300)   NOT NULL,
    quantity       numeric(14, 3) NOT NULL CHECK (quantity > 0),
    unit           varchar(32)    NOT NULL,
    tag            varchar(120),                 -- asset tag or serial numbers
    location       varchar(200),
    value_minor    bigint         CHECK (value_minor >= 0),
    updated_at     timestamptz    NOT NULL,
    version        bigint         NOT NULL DEFAULT 0
);
CREATE INDEX handover_assets_project_idx ON closeout.handover_assets (project_id, sort_order);

CREATE TABLE closeout.lessons (
    id               uuid PRIMARY KEY,
    tenant_id        uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id       uuid          NOT NULL REFERENCES portfolio.projects (id),
    kind             varchar(10)   NOT NULL CHECK (kind IN ('WENT_WELL', 'IMPROVE')),
    category         varchar(16)   NOT NULL
                     CHECK (category IN ('PROCUREMENT', 'DESIGN', 'SCHEDULE', 'COST', 'CONTRACTOR', 'STAKEHOLDERS',
                                         'WAYLEAVE', 'SAFETY', 'TECHNICAL', 'OTHER')),
    phase            varchar(16)   CHECK (phase IN ('INITIATION', 'PROCUREMENT', 'PLANNING', 'EXECUTION',
                                                    'CLOSEOUT', 'IMPLEMENTATION')),
    title            varchar(200)  NOT NULL,
    what_happened    varchar(4000) NOT NULL,
    recommendation   varchar(4000),
    status           varchar(10)   NOT NULL CHECK (status IN ('SUBMITTED', 'ADOPTED', 'NOTED')),
    review_note      varchar(2000),
    reviewed_by      uuid          REFERENCES platform.users (id),
    reviewed_at      timestamptz,
    created_by       uuid          NOT NULL REFERENCES platform.users (id),
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0
);
CREATE INDEX lessons_tenant_idx ON closeout.lessons (tenant_id, created_at DESC);

CREATE TABLE closeout.documents (
    id             uuid PRIMARY KEY,
    tenant_id      uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id     uuid          NOT NULL REFERENCES closeout.closeouts (project_id),
    kind           varchar(10)   NOT NULL CHECK (kind IN ('EVIDENCE', 'DEFECT', 'HANDOVER')),
    ref_id         uuid,                         -- checklist item or defect
    file_name      varchar(255)  NOT NULL,
    content_type   varchar(255)  NOT NULL,
    size_bytes     bigint        NOT NULL,
    sha256         varchar(64)   NOT NULL,
    storage_key    varchar(500)  NOT NULL,
    uploaded_by    uuid          NOT NULL REFERENCES platform.users (id),
    uploaded_at    timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    version        bigint        NOT NULL DEFAULT 0
);
CREATE INDEX closeout_documents_project_idx ON closeout.documents (project_id);
