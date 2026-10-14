-- V2: procurement configuration, held per tenant so each organisation sets its own
-- rules without code changes:
--   1. procurement.methods                    which methods are used, and their value ranges
--   2. procurement.approval_thresholds        which amounts need which approver(s)
--   3. procurement.evaluation_committee_rules committee size, two-envelope opening, signatories
--
-- Amounts are integer minor units (cents) in the stated currency. Every change made
-- through the API is written to platform.audit_events.

CREATE SCHEMA IF NOT EXISTS procurement;

-- ---------------------------------------------------------------------------
-- 1. Procurement methods
-- ---------------------------------------------------------------------------
CREATE TABLE procurement.methods (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid          NOT NULL REFERENCES platform.tenants (id),
    code                    varchar(40)   NOT NULL,   -- OPEN_TENDER, RESTRICTED_TENDER, RFQ, DIRECT, FRAMEWORK, ...
    name                    varchar(120)  NOT NULL,
    description             varchar(1000),
    enabled                 boolean       NOT NULL DEFAULT true,
    -- Estimated value range this method may be used for. NULL = no limit.
    min_value_minor         bigint        CHECK (min_value_minor >= 0),
    max_value_minor         bigint        CHECK (max_value_minor >= 0),
    currency                varchar(3)    NOT NULL DEFAULT 'KES',
    min_bid_period_days     integer       NOT NULL DEFAULT 0 CHECK (min_bid_period_days >= 0),
    requires_public_advert  boolean       NOT NULL DEFAULT false,
    requires_justification  boolean       NOT NULL DEFAULT false,  -- e.g. direct procurement
    sort_order              integer       NOT NULL DEFAULT 0,
    updated_at              timestamptz   NOT NULL,
    version                 bigint        NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code),
    CHECK (min_value_minor IS NULL OR max_value_minor IS NULL OR min_value_minor <= max_value_minor)
);

-- ---------------------------------------------------------------------------
-- 2. Approval thresholds
--    One row = one approver step for an amount band. Several rows with the same
--    band and different step_order form an approval chain (step 1, then 2, ...).
--    method_code NULL = applies to every method; a method-specific band takes
--    precedence over the general bands for that stage.
-- ---------------------------------------------------------------------------
CREATE TABLE procurement.approval_thresholds (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid          NOT NULL REFERENCES platform.tenants (id),
    stage              varchar(32)   NOT NULL
                       CHECK (stage IN ('REQUISITION', 'AWARD', 'CONTRACT_VARIATION')),
    method_code        varchar(40),
    min_amount_minor   bigint        NOT NULL DEFAULT 0 CHECK (min_amount_minor >= 0),
    max_amount_minor   bigint        CHECK (max_amount_minor >= 0),   -- NULL = no upper limit
    currency           varchar(3)    NOT NULL DEFAULT 'KES',
    step_order         integer       NOT NULL DEFAULT 1 CHECK (step_order >= 1),
    approver_role      varchar(64)   NOT NULL,   -- e.g. HEAD_OF_DEPARTMENT, ACCOUNTING_OFFICER
    active             boolean       NOT NULL DEFAULT true,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    CHECK (max_amount_minor IS NULL OR min_amount_minor < max_amount_minor)
);

CREATE INDEX approval_thresholds_lookup_idx
    ON procurement.approval_thresholds (tenant_id, stage, method_code, min_amount_minor);

-- ---------------------------------------------------------------------------
-- 3. Evaluation committee rules
--    method_code NULL = the default rule; a method-specific row overrides it.
-- ---------------------------------------------------------------------------
CREATE TABLE procurement.evaluation_committee_rules (
    id                          uuid PRIMARY KEY,
    tenant_id                   uuid          NOT NULL REFERENCES platform.tenants (id),
    method_code                 varchar(40),
    min_members                 integer       NOT NULL CHECK (min_members >= 1),
    max_members                 integer       NOT NULL,
    quorum                      integer       NOT NULL CHECK (quorum >= 1),
    -- true = technical bids opened and scored first; financial bids opened only for
    -- bidders that pass the technical evaluation.
    two_envelope                boolean       NOT NULL DEFAULT false,
    technical_pass_mark_pct     integer       CHECK (technical_pass_mark_pct BETWEEN 0 AND 100),
    requires_coi_declaration    boolean       NOT NULL DEFAULT true,
    -- Who signs the evaluation report.
    report_signatories          varchar(32)   NOT NULL
                                CHECK (report_signatories IN ('ALL_MEMBERS', 'CHAIR_AND_SECRETARY', 'CHAIR_ONLY')),
    -- Who reviews the report next (e.g. head of procurement's professional opinion).
    report_reviewer_role        varchar(64),
    -- Who approves the award recommendation.
    award_approver_role         varchar(64)   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    version                     bigint        NOT NULL DEFAULT 0,
    UNIQUE NULLS NOT DISTINCT (tenant_id, method_code),
    CHECK (min_members <= max_members),
    CHECK (quorum <= min_members)
);
