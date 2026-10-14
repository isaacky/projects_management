-- V3: user roles, in-app notifications, and purchase requisitions with approval chains.

-- ---------------------------------------------------------------------------
-- Roles held by each user (role codes match approver roles in the procurement
-- settings, e.g. HEAD_OF_DEPARTMENT, ACCOUNTING_OFFICER).
-- ---------------------------------------------------------------------------
CREATE TABLE platform.user_roles (
    user_id    uuid        NOT NULL REFERENCES platform.users (id) ON DELETE CASCADE,
    role_code  varchar(64) NOT NULL,
    PRIMARY KEY (user_id, role_code)
);

CREATE INDEX user_roles_role_idx ON platform.user_roles (role_code);

-- ---------------------------------------------------------------------------
-- In-app notifications (the inbox behind the bell icon).
-- ---------------------------------------------------------------------------
CREATE TABLE platform.notifications (
    id            uuid PRIMARY KEY,
    tenant_id     uuid          NOT NULL REFERENCES platform.tenants (id),
    recipient_id  uuid          NOT NULL REFERENCES platform.users (id),
    type          varchar(64)   NOT NULL,
    title         varchar(200)  NOT NULL,
    body          varchar(1000),
    link          varchar(300),
    created_at    timestamptz   NOT NULL,
    read_at       timestamptz
);

CREATE INDEX notifications_inbox_idx
    ON platform.notifications (recipient_id, created_at DESC);
CREATE INDEX notifications_unread_idx
    ON platform.notifications (recipient_id) WHERE read_at IS NULL;

-- ---------------------------------------------------------------------------
-- Requisitions
-- ---------------------------------------------------------------------------
-- Per-tenant, per-year running number: REQ-2026-0001, REQ-2026-0002, ...
CREATE TABLE procurement.requisition_counters (
    tenant_id   uuid    NOT NULL REFERENCES platform.tenants (id),
    year        integer NOT NULL,
    next_value  integer NOT NULL,
    PRIMARY KEY (tenant_id, year)
);

CREATE TABLE procurement.requisitions (
    id                     uuid PRIMARY KEY,
    tenant_id              uuid          NOT NULL REFERENCES platform.tenants (id),
    number                 varchar(32)   NOT NULL,
    project_id             uuid          NOT NULL REFERENCES portfolio.projects (id),
    title                  varchar(200)  NOT NULL,
    justification          varchar(4000),
    department             varchar(120),
    needed_by              date,
    currency               varchar(3)    NOT NULL,
    estimated_total_minor  bigint        NOT NULL DEFAULT 0 CHECK (estimated_total_minor >= 0),
    proposed_method_code   varchar(40),
    status                 varchar(16)   NOT NULL
                           CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED',
                                             'RETURNED', 'CANCELLED')),
    requested_by           uuid          NOT NULL REFERENCES platform.users (id),
    current_round          integer       NOT NULL DEFAULT 0,
    submitted_at           timestamptz,
    decided_at             timestamptz,
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    version                bigint        NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, number)
);

CREATE INDEX requisitions_tenant_status_idx ON procurement.requisitions (tenant_id, status);
CREATE INDEX requisitions_requester_idx ON procurement.requisitions (tenant_id, requested_by);
CREATE INDEX requisitions_project_idx ON procurement.requisitions (project_id);

CREATE TABLE procurement.requisition_lines (
    id                uuid PRIMARY KEY,
    requisition_id    uuid           NOT NULL REFERENCES procurement.requisitions (id) ON DELETE CASCADE,
    line_no           integer        NOT NULL CHECK (line_no >= 1),
    description       varchar(500)   NOT NULL,
    quantity          numeric(14, 3) NOT NULL CHECK (quantity > 0),
    unit              varchar(32)    NOT NULL,
    unit_price_minor  bigint         NOT NULL CHECK (unit_price_minor >= 0),
    total_minor       bigint         NOT NULL CHECK (total_minor >= 0),
    version           bigint         NOT NULL DEFAULT 0
);

CREATE INDEX requisition_lines_req_idx ON procurement.requisition_lines (requisition_id, line_no);

-- One row per approval step per submission round. The chain is copied from the
-- procurement settings when the requisition is submitted, so later settings
-- changes never alter a requisition already in approval.
CREATE TABLE procurement.requisition_approvals (
    id              uuid PRIMARY KEY,
    requisition_id  uuid          NOT NULL REFERENCES procurement.requisitions (id) ON DELETE CASCADE,
    round           integer       NOT NULL CHECK (round >= 1),
    step_order      integer       NOT NULL CHECK (step_order >= 1),
    approver_role   varchar(64)   NOT NULL,
    status          varchar(16)   NOT NULL
                    CHECK (status IN ('WAITING', 'PENDING', 'APPROVED', 'REJECTED',
                                      'RETURNED', 'CANCELLED')),
    decided_by      uuid          REFERENCES platform.users (id),
    decided_at      timestamptz,
    comment         varchar(2000),
    version         bigint        NOT NULL DEFAULT 0,
    UNIQUE (requisition_id, round, step_order)
);

CREATE INDEX requisition_approvals_pending_idx
    ON procurement.requisition_approvals (approver_role) WHERE status = 'PENDING';
