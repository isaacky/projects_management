-- V7: contracts. A contract is made from an awarded tender, signed after the standstill
-- period, then managed through milestones, invoices and payments, variations, completion
-- and the supplier's performance rating. Amounts are integer minor units.

CREATE SCHEMA IF NOT EXISTS contracts;

CREATE TABLE contracts.contract_counters (
    tenant_id   uuid    NOT NULL REFERENCES platform.tenants (id),
    year        integer NOT NULL,
    next_value  integer NOT NULL,
    PRIMARY KEY (tenant_id, year)
);

CREATE TABLE contracts.contracts (
    id                        uuid PRIMARY KEY,
    tenant_id                 uuid          NOT NULL REFERENCES platform.tenants (id),
    number                    varchar(32)   NOT NULL,
    tender_id                 uuid          NOT NULL REFERENCES procurement.tenders (id),
    bid_id                    uuid          NOT NULL REFERENCES procurement.bids (id),
    supplier_id               uuid          NOT NULL REFERENCES procurement.suppliers (id),
    project_id                uuid          NOT NULL REFERENCES portfolio.projects (id),
    requisition_id            uuid          NOT NULL REFERENCES procurement.requisitions (id),
    title                     varchar(200)  NOT NULL,
    status                    varchar(16)   NOT NULL
                              CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'TERMINATED', 'CANCELLED')),
    currency                  varchar(3)    NOT NULL,
    original_value_minor      bigint        NOT NULL CHECK (original_value_minor >= 0),
    current_value_minor       bigint        NOT NULL CHECK (current_value_minor >= 0),
    -- Retention withheld from each payment, in basis points (1000 = 10%).
    retention_bp              integer       NOT NULL DEFAULT 0 CHECK (retention_bp BETWEEN 0 AND 2000),
    defects_liability_days    integer       NOT NULL DEFAULT 0 CHECK (defects_liability_days >= 0),
    performance_security_minor bigint       NOT NULL DEFAULT 0 CHECK (performance_security_minor >= 0),
    start_date                date,
    original_end_date         date,
    end_date                  date,
    contract_manager_id       uuid          REFERENCES platform.users (id),
    standstill_ends_at        timestamptz   NOT NULL,
    -- Request for review to the Review Board after the award notice.
    review_status             varchar(16)   NOT NULL DEFAULT 'NONE'
                              CHECK (review_status IN ('NONE', 'LODGED', 'DISMISSED', 'UPHELD')),
    review_note               varchar(2000),
    signed_at                 date,
    supplier_signatory        varchar(200),
    entity_signatory          varchar(200),
    signed_recorded_by        uuid          REFERENCES platform.users (id),
    completed_at              date,
    ended_reason              varchar(2000),
    rating_quality            integer       CHECK (rating_quality BETWEEN 1 AND 5),
    rating_timeliness         integer       CHECK (rating_timeliness BETWEEN 1 AND 5),
    rating_compliance         integer       CHECK (rating_compliance BETWEEN 1 AND 5),
    rating_comment            varchar(2000),
    rated_by                  uuid          REFERENCES platform.users (id),
    created_by                uuid          NOT NULL REFERENCES platform.users (id),
    created_at                timestamptz   NOT NULL,
    updated_at                timestamptz   NOT NULL,
    version                   bigint        NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, number),
    CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
-- One live contract per tender.
CREATE UNIQUE INDEX contracts_one_per_tender ON contracts.contracts (tender_id) WHERE status <> 'CANCELLED';
CREATE INDEX contracts_project_idx ON contracts.contracts (tenant_id, project_id);
CREATE INDEX contracts_supplier_idx ON contracts.contracts (tenant_id, supplier_id);

CREATE TABLE contracts.securities (
    id                  uuid PRIMARY KEY,
    contract_id         uuid          NOT NULL REFERENCES contracts.contracts (id) ON DELETE CASCADE,
    kind                varchar(24)   NOT NULL CHECK (kind IN ('PERFORMANCE', 'ADVANCE_PAYMENT')),
    form                varchar(24)   NOT NULL CHECK (form IN ('BANK_GUARANTEE', 'INSURANCE_BOND', 'OTHER')),
    amount_minor        bigint        NOT NULL CHECK (amount_minor > 0),
    issuer              varchar(200)  NOT NULL,
    reference           varchar(100)  NOT NULL,
    expiry_date         date          NOT NULL,
    status              varchar(16)   NOT NULL CHECK (status IN ('HELD', 'RELEASED', 'CALLED')),
    received_at         date          NOT NULL,
    recorded_by         uuid          NOT NULL REFERENCES platform.users (id),
    expiry_notified_at  timestamptz,
    closed_note         varchar(1000),
    version             bigint        NOT NULL DEFAULT 0
);
CREATE INDEX securities_expiry_idx ON contracts.securities (status, expiry_date);

CREATE TABLE contracts.milestones (
    id              uuid PRIMARY KEY,
    contract_id     uuid          NOT NULL REFERENCES contracts.contracts (id) ON DELETE CASCADE,
    sort_order      integer       NOT NULL,
    title           varchar(200)  NOT NULL,
    description     varchar(1000),
    due_date        date,
    amount_minor    bigint        NOT NULL CHECK (amount_minor >= 0),
    status          varchar(16)   NOT NULL CHECK (status IN ('PLANNED', 'ACCEPTED', 'CANCELLED')),
    accepted_at     date,
    accepted_by     uuid          REFERENCES platform.users (id),
    acceptance_note varchar(2000),
    version         bigint        NOT NULL DEFAULT 0
);
CREATE INDEX milestones_contract_idx ON contracts.milestones (contract_id, sort_order);

-- Supplier invoices and the payment certificate built on each: received -> certified ->
-- approved -> paid (or rejected). A retention release is an invoice of kind RETENTION_RELEASE.
CREATE TABLE contracts.invoices (
    id                 uuid PRIMARY KEY,
    contract_id        uuid          NOT NULL REFERENCES contracts.contracts (id) ON DELETE CASCADE,
    kind               varchar(24)   NOT NULL CHECK (kind IN ('INVOICE', 'RETENTION_RELEASE')),
    certificate_no     integer       NOT NULL,
    supplier_ref       varchar(100),
    invoice_date       date          NOT NULL,
    milestone_id       uuid          REFERENCES contracts.milestones (id),
    gross_minor        bigint        NOT NULL CHECK (gross_minor > 0),
    retention_minor    bigint        NOT NULL DEFAULT 0 CHECK (retention_minor >= 0),
    status             varchar(16)   NOT NULL CHECK (status IN ('RECEIVED', 'CERTIFIED', 'APPROVED', 'PAID', 'REJECTED')),
    received_by        uuid          NOT NULL REFERENCES platform.users (id),
    received_at        timestamptz   NOT NULL,
    certified_by       uuid          REFERENCES platform.users (id),
    certified_at       timestamptz,
    certificate_note   varchar(2000),
    approved_by        uuid          REFERENCES platform.users (id),
    approved_at        timestamptz,
    paid_by            uuid          REFERENCES platform.users (id),
    paid_on            date,
    payment_ref        varchar(100),
    rejected_reason    varchar(2000),
    version            bigint        NOT NULL DEFAULT 0,
    UNIQUE (contract_id, certificate_no),
    CHECK (retention_minor <= gross_minor)
);

CREATE TABLE contracts.variations (
    id                 uuid PRIMARY KEY,
    contract_id        uuid          NOT NULL REFERENCES contracts.contracts (id) ON DELETE CASCADE,
    number             integer       NOT NULL,
    value_change_minor bigint        NOT NULL DEFAULT 0,
    new_end_date       date,
    reason             varchar(2000) NOT NULL,
    status             varchar(16)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by       uuid          NOT NULL REFERENCES platform.users (id),
    requested_at       timestamptz   NOT NULL,
    decided_at         timestamptz,
    version            bigint        NOT NULL DEFAULT 0,
    UNIQUE (contract_id, number)
);

CREATE TABLE contracts.variation_approvals (
    id              uuid PRIMARY KEY,
    variation_id    uuid          NOT NULL REFERENCES contracts.variations (id) ON DELETE CASCADE,
    step_order      integer       NOT NULL,
    approver_role   varchar(64)   NOT NULL,
    status          varchar(16)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SKIPPED')),
    decided_by      uuid          REFERENCES platform.users (id),
    decided_at      timestamptz,
    note            varchar(2000),
    version         bigint        NOT NULL DEFAULT 0,
    UNIQUE (variation_id, step_order)
);

CREATE TABLE contracts.documents (
    id              uuid PRIMARY KEY,
    contract_id     uuid          NOT NULL REFERENCES contracts.contracts (id) ON DELETE CASCADE,
    kind            varchar(24)   NOT NULL
                    CHECK (kind IN ('SIGNED_CONTRACT', 'SECURITY', 'INSPECTION', 'INVOICE', 'VARIATION', 'OTHER')),
    ref_id          uuid,          -- the security, milestone, invoice or variation it belongs to
    file_name       varchar(255)  NOT NULL,
    content_type    varchar(127)  NOT NULL,
    size_bytes      bigint        NOT NULL CHECK (size_bytes >= 0),
    sha256          varchar(64)   NOT NULL,
    storage_key     varchar(300)  NOT NULL,
    uploaded_by     uuid          NOT NULL REFERENCES platform.users (id),
    uploaded_at     timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0
);
CREATE INDEX contract_documents_idx ON contracts.documents (contract_id, uploaded_at);
