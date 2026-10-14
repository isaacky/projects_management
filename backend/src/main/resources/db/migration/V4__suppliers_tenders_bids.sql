-- V4: supplier register, tenders, sealed bids and bid openings.

-- ---------------------------------------------------------------------------
-- Supplier register
-- ---------------------------------------------------------------------------
CREATE TABLE procurement.suppliers (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid          NOT NULL REFERENCES platform.tenants (id),
    name               varchar(200)  NOT NULL,
    kra_pin            varchar(11)   NOT NULL,            -- e.g. P051234567X
    registration_no    varchar(64),
    email              varchar(320),
    phone              varchar(32),
    address            varchar(500),
    categories         varchar(500),                      -- free text, e.g. "Transformers; Cables"
    agpo_category      varchar(8)    CHECK (agpo_category IN ('YOUTH', 'WOMEN', 'PWD')),
    agpo_certificate_no varchar(64),
    agpo_expiry        date,
    status             varchar(16)   NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEBARRED')),
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, kra_pin)
);

CREATE INDEX suppliers_tenant_name_idx ON procurement.suppliers (tenant_id, lower(name));

-- ---------------------------------------------------------------------------
-- Tenders
-- ---------------------------------------------------------------------------
CREATE TABLE procurement.tender_counters (
    tenant_id   uuid    NOT NULL REFERENCES platform.tenants (id),
    year        integer NOT NULL,
    next_value  integer NOT NULL,
    PRIMARY KEY (tenant_id, year)
);

CREATE TABLE procurement.tenders (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid          NOT NULL REFERENCES platform.tenants (id),
    number                  varchar(32)   NOT NULL,
    requisition_id          uuid          NOT NULL REFERENCES procurement.requisitions (id),
    project_id              uuid          NOT NULL REFERENCES portfolio.projects (id),
    title                   varchar(200)  NOT NULL,
    description             varchar(4000),
    method_code             varchar(40)   NOT NULL,
    currency                varchar(3)    NOT NULL,
    estimated_value_minor   bigint        NOT NULL CHECK (estimated_value_minor >= 0),
    -- Copied from the procurement settings when the tender is created:
    two_envelope            boolean       NOT NULL,
    min_bid_period_days     integer       NOT NULL,
    opening_quorum          integer       NOT NULL CHECK (opening_quorum >= 1),
    invited_only            boolean       NOT NULL,         -- method is not publicly advertised
    status                  varchar(24)   NOT NULL
                            CHECK (status IN ('DRAFT', 'PUBLISHED', 'TECHNICAL_OPENED', 'OPENED', 'CANCELLED')),
    closing_at              timestamptz,
    published_at            timestamptz,
    cancelled_reason        varchar(2000),
    -- Per-tender data key, encrypted under the master key (never stored in clear).
    wrapped_key             bytea         NOT NULL,
    created_by              uuid          NOT NULL REFERENCES platform.users (id),
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    version                 bigint        NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, number)
);

-- One live tender per requisition (a cancelled tender frees the requisition).
CREATE UNIQUE INDEX tenders_one_live_per_requisition
    ON procurement.tenders (requisition_id) WHERE status <> 'CANCELLED';
CREATE INDEX tenders_tenant_status_idx ON procurement.tenders (tenant_id, status);

CREATE TABLE procurement.tender_invitations (
    tender_id    uuid        NOT NULL REFERENCES procurement.tenders (id) ON DELETE CASCADE,
    supplier_id  uuid        NOT NULL REFERENCES procurement.suppliers (id),
    invited_at   timestamptz NOT NULL,
    PRIMARY KEY (tender_id, supplier_id)
);

-- Tender documents (not sealed): specifications, bid forms, addenda.
CREATE TABLE procurement.tender_documents (
    id            uuid PRIMARY KEY,
    tender_id     uuid          NOT NULL REFERENCES procurement.tenders (id) ON DELETE CASCADE,
    file_name     varchar(255)  NOT NULL,
    content_type  varchar(127)  NOT NULL,
    size_bytes    bigint        NOT NULL CHECK (size_bytes >= 0),
    sha256        varchar(64)   NOT NULL,
    storage_key   varchar(300)  NOT NULL UNIQUE,
    uploaded_by   uuid          NOT NULL REFERENCES platform.users (id),
    uploaded_at   timestamptz   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Bids (recorded by procurement staff for the pilot)
-- ---------------------------------------------------------------------------
CREATE TABLE procurement.bids (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid          NOT NULL REFERENCES platform.tenants (id),
    tender_id            uuid          NOT NULL REFERENCES procurement.tenders (id),
    supplier_id          uuid          NOT NULL REFERENCES procurement.suppliers (id),
    status               varchar(16)   NOT NULL CHECK (status IN ('RECEIVED', 'WITHDRAWN')),
    receipt_no           varchar(48)   NOT NULL,
    received_at          timestamptz   NOT NULL,
    received_by          uuid          NOT NULL REFERENCES platform.users (id),
    delivery_note        varchar(500),             -- e.g. "Hand delivered, 2 sealed envelopes"
    receipt_hash         varchar(64)   NOT NULL,   -- SHA-256 over the sealed files, given to the bidder
    withdrawn_at         timestamptz,
    withdraw_reason      varchar(1000),
    -- Read out and recorded at the (financial) opening; NULL until then.
    read_out_price_minor bigint        CHECK (read_out_price_minor >= 0),
    read_out_note        varchar(1000),
    version              bigint        NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, receipt_no)
);

-- One live bid per supplier per tender.
CREATE UNIQUE INDEX bids_one_live_per_supplier
    ON procurement.bids (tender_id, supplier_id) WHERE status = 'RECEIVED';
CREATE INDEX bids_tender_idx ON procurement.bids (tender_id);

-- Sealed files: stored encrypted in object storage; readable only after their envelope is opened.
CREATE TABLE procurement.bid_files (
    id            uuid PRIMARY KEY,
    bid_id        uuid          NOT NULL REFERENCES procurement.bids (id) ON DELETE CASCADE,
    envelope      varchar(16)   NOT NULL CHECK (envelope IN ('SINGLE', 'TECHNICAL', 'FINANCIAL')),
    file_name     varchar(255)  NOT NULL,
    content_type  varchar(127)  NOT NULL,
    size_bytes    bigint        NOT NULL CHECK (size_bytes >= 0),
    sha256        varchar(64)   NOT NULL,          -- of the original (plaintext) file
    storage_key   varchar(300)  NOT NULL UNIQUE,
    file_nonce    bytea         NOT NULL,
    added_after_opening boolean NOT NULL DEFAULT false,  -- e.g. scans of paper bids
    uploaded_by   uuid          NOT NULL REFERENCES platform.users (id),
    uploaded_at   timestamptz   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0
);

CREATE INDEX bid_files_bid_idx ON procurement.bid_files (bid_id);

-- ---------------------------------------------------------------------------
-- Bid openings (one per envelope: SINGLE, or TECHNICAL then FINANCIAL)
-- ---------------------------------------------------------------------------
CREATE TABLE procurement.bid_openings (
    id          uuid PRIMARY KEY,
    tenant_id   uuid          NOT NULL REFERENCES platform.tenants (id),
    tender_id   uuid          NOT NULL REFERENCES procurement.tenders (id),
    envelope    varchar(16)   NOT NULL CHECK (envelope IN ('SINGLE', 'TECHNICAL', 'FINANCIAL')),
    opened_at   timestamptz   NOT NULL,
    opened_by   uuid          NOT NULL REFERENCES platform.users (id),
    venue       varchar(200),
    notes       varchar(2000),
    version     bigint        NOT NULL DEFAULT 0,
    UNIQUE (tender_id, envelope)
);

CREATE TABLE procurement.bid_opening_attendees (
    opening_id  uuid NOT NULL REFERENCES procurement.bid_openings (id) ON DELETE CASCADE,
    user_id     uuid NOT NULL REFERENCES platform.users (id),
    PRIMARY KEY (opening_id, user_id)
);
