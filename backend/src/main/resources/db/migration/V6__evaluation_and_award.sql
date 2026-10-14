-- V6: bid evaluation and award.
--
--   Settings (per tenant, edited under Procurement settings):
--     procurement.evaluation_templates           named sets of evaluation criteria, per method
--     procurement.evaluation_template_criteria   pass/fail (preliminary) and scored (technical) criteria
--     procurement.methods.standstill_days        days between award notice and contract signature
--
--   Per tender:
--     procurement.evaluations              one evaluation per tender, its stage and recommendation
--     procurement.evaluation_members       the appointed committee, declarations and report signatures
--     procurement.evaluation_criteria      criteria copied from a template when the evaluation is set up
--     procurement.evaluation_scores        each member's own marks
--     procurement.evaluation_bid_results   the committee's result per bid when a stage is closed
--     procurement.evaluation_approvals     professional opinion and award approval steps
--
--   Tenders may be reserved for AGPO groups, and gain AWARDED / TERMINATED outcomes.

-- ---------------------------------------------------------------------------
-- Settings
-- ---------------------------------------------------------------------------
ALTER TABLE procurement.methods
    ADD COLUMN standstill_days integer NOT NULL DEFAULT 14 CHECK (standstill_days >= 0);

CREATE TABLE procurement.evaluation_templates (
    id            uuid PRIMARY KEY,
    tenant_id     uuid          NOT NULL REFERENCES platform.tenants (id),
    code          varchar(40)   NOT NULL,
    name          varchar(120)  NOT NULL,
    method_code   varchar(40),                 -- NULL = usable for any method
    description   varchar(1000),
    active        boolean       NOT NULL DEFAULT true,
    updated_at    timestamptz   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE procurement.evaluation_template_criteria (
    template_id   uuid          NOT NULL REFERENCES procurement.evaluation_templates (id) ON DELETE CASCADE,
    sort_order    integer       NOT NULL,
    stage         varchar(16)   NOT NULL CHECK (stage IN ('PRELIMINARY', 'TECHNICAL')),
    name          varchar(200)  NOT NULL,
    description   varchar(1000),
    max_score     numeric(6, 2),               -- technical criteria only
    PRIMARY KEY (template_id, sort_order),
    CHECK ((stage = 'PRELIMINARY' AND max_score IS NULL) OR (stage = 'TECHNICAL' AND max_score > 0))
);

-- ---------------------------------------------------------------------------
-- Tenders and bids
-- ---------------------------------------------------------------------------
ALTER TABLE procurement.tenders DROP CONSTRAINT tenders_status_check;
ALTER TABLE procurement.tenders ADD CONSTRAINT tenders_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'TECHNICAL_OPENED', 'OPENED', 'AWARDED', 'TERMINATED', 'CANCELLED'));

ALTER TABLE procurement.tenders
    -- Reserved for Access to Government Procurement Opportunities groups; AGPO = any of them.
    ADD COLUMN reserved_group        varchar(8) CHECK (reserved_group IN ('YOUTH', 'WOMEN', 'PWD', 'AGPO')),
    -- True once the single or financial envelope is opened (stays true after award or termination).
    ADD COLUMN prices_opened         boolean NOT NULL DEFAULT false,
    ADD COLUMN awarded_bid_id        uuid REFERENCES procurement.bids (id),
    ADD COLUMN awarded_amount_minor  bigint CHECK (awarded_amount_minor >= 0),
    ADD COLUMN awarded_at            timestamptz,
    ADD COLUMN standstill_ends_at    timestamptz;

UPDATE procurement.tenders SET prices_opened = true WHERE status = 'OPENED';

-- A terminated tender frees the requisition for a new tender, like a cancelled one.
DROP INDEX procurement.tenders_one_live_per_requisition;
CREATE UNIQUE INDEX tenders_one_live_per_requisition
    ON procurement.tenders (requisition_id) WHERE status NOT IN ('CANCELLED', 'TERMINATED');

ALTER TABLE procurement.bids
    ADD COLUMN disqualified_stage  varchar(16) CHECK (disqualified_stage IN ('PRELIMINARY', 'TECHNICAL', 'FINANCIAL')),
    ADD COLUMN disqualified_reason varchar(2000),
    ADD CHECK ((disqualified_stage IS NULL) = (disqualified_reason IS NULL));

-- ---------------------------------------------------------------------------
-- Evaluation
-- ---------------------------------------------------------------------------
CREATE TABLE procurement.evaluations (
    id                        uuid PRIMARY KEY,
    tenant_id                 uuid          NOT NULL REFERENCES platform.tenants (id),
    tender_id                 uuid          NOT NULL UNIQUE REFERENCES procurement.tenders (id),
    status                    varchar(16)   NOT NULL
                              CHECK (status IN ('SETUP', 'PRELIMINARY', 'TECHNICAL', 'FINANCIAL', 'REPORT',
                                                'SIGNING', 'APPROVAL', 'AWARDED', 'TERMINATED')),
    template_code             varchar(40),
    technical_pass_mark_pct   integer       CHECK (technical_pass_mark_pct BETWEEN 0 AND 100),
    -- Copied from the committee rule when the evaluation is set up:
    min_members               integer       NOT NULL,
    max_members               integer       NOT NULL,
    quorum                    integer       NOT NULL,
    requires_coi_declaration  boolean       NOT NULL,
    report_signatories        varchar(32)   NOT NULL,
    report_reviewer_role      varchar(64),
    award_approver_role       varchar(64)   NOT NULL,
    created_by                uuid          NOT NULL REFERENCES platform.users (id),
    created_at                timestamptz   NOT NULL,
    appointed_by              uuid          REFERENCES platform.users (id),
    appointed_at              timestamptz,
    preliminary_closed_at     timestamptz,
    technical_closed_at       timestamptz,
    financial_closed_at       timestamptz,
    -- Report round: goes up each time an approver returns the report to the committee.
    round                     integer       NOT NULL DEFAULT 1,
    no_award                  boolean       NOT NULL DEFAULT false,
    recommended_bid_id        uuid          REFERENCES procurement.bids (id),
    recommended_amount_minor  bigint,
    recommendation            varchar(4000),
    report_submitted_by       uuid          REFERENCES platform.users (id),
    report_submitted_at       timestamptz,
    decided_at                timestamptz,
    updated_at                timestamptz   NOT NULL,
    version                   bigint        NOT NULL DEFAULT 0
);
CREATE INDEX evaluations_tenant_idx ON procurement.evaluations (tenant_id, status);

CREATE TABLE procurement.evaluation_members (
    id                uuid PRIMARY KEY,
    evaluation_id     uuid          NOT NULL REFERENCES procurement.evaluations (id) ON DELETE CASCADE,
    user_id           uuid          NOT NULL REFERENCES platform.users (id),
    role              varchar(16)   NOT NULL CHECK (role IN ('CHAIR', 'MEMBER', 'SECRETARY')),
    added_at          timestamptz   NOT NULL,
    -- Conflict of interest declaration, made once the bidders are known.
    declared_at       timestamptz,
    has_conflict      boolean,
    declaration       varchar(2000),
    -- Report signature for the current round (signed_round = evaluations.round).
    signed_at         timestamptz,
    signed_round      integer,
    sign_note         varchar(2000),
    version           bigint        NOT NULL DEFAULT 0,
    UNIQUE (evaluation_id, user_id),
    CHECK ((declared_at IS NULL) = (has_conflict IS NULL))
);

CREATE TABLE procurement.evaluation_criteria (
    id              uuid PRIMARY KEY,
    evaluation_id   uuid          NOT NULL REFERENCES procurement.evaluations (id) ON DELETE CASCADE,
    stage           varchar(16)   NOT NULL CHECK (stage IN ('PRELIMINARY', 'TECHNICAL')),
    sort_order      integer       NOT NULL,
    name            varchar(200)  NOT NULL,
    description     varchar(1000),
    max_score       numeric(6, 2),
    version         bigint        NOT NULL DEFAULT 0,
    CHECK ((stage = 'PRELIMINARY' AND max_score IS NULL) OR (stage = 'TECHNICAL' AND max_score > 0))
);
CREATE INDEX evaluation_criteria_eval_idx ON procurement.evaluation_criteria (evaluation_id, stage, sort_order);

CREATE TABLE procurement.evaluation_scores (
    id              uuid PRIMARY KEY,
    evaluation_id   uuid          NOT NULL REFERENCES procurement.evaluations (id) ON DELETE CASCADE,
    criterion_id    uuid          NOT NULL REFERENCES procurement.evaluation_criteria (id) ON DELETE CASCADE,
    bid_id          uuid          NOT NULL REFERENCES procurement.bids (id),
    member_id       uuid          NOT NULL REFERENCES platform.users (id),
    passed          boolean,                   -- preliminary criteria
    score           numeric(6, 2),             -- technical criteria
    comment         varchar(1000),
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    UNIQUE (criterion_id, bid_id, member_id),
    CHECK ((passed IS NULL) <> (score IS NULL)),
    CHECK (score IS NULL OR score >= 0)
);
CREATE INDEX evaluation_scores_eval_idx ON procurement.evaluation_scores (evaluation_id);

CREATE TABLE procurement.evaluation_bid_results (
    id                     uuid PRIMARY KEY,
    evaluation_id          uuid          NOT NULL REFERENCES procurement.evaluations (id) ON DELETE CASCADE,
    bid_id                 uuid          NOT NULL REFERENCES procurement.bids (id),
    preliminary_passed     boolean,
    preliminary_note       varchar(2000),
    technical_score        numeric(8, 2),
    technical_pct          numeric(5, 2),
    technical_passed       boolean,
    evaluated_price_minor  bigint        CHECK (evaluated_price_minor >= 0),
    price_note             varchar(2000),
    financial_rank         integer,
    updated_at             timestamptz   NOT NULL,
    version                bigint        NOT NULL DEFAULT 0,
    UNIQUE (evaluation_id, bid_id)
);

CREATE TABLE procurement.evaluation_approvals (
    id              uuid PRIMARY KEY,
    evaluation_id   uuid          NOT NULL REFERENCES procurement.evaluations (id) ON DELETE CASCADE,
    round           integer       NOT NULL,
    step_order      integer       NOT NULL,
    approver_role   varchar(64)   NOT NULL,
    opinion         boolean       NOT NULL,     -- the head of procurement's professional opinion
    status          varchar(16)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'RETURNED', 'TERMINATED', 'SKIPPED')),
    decided_by      uuid          REFERENCES platform.users (id),
    decided_at      timestamptz,
    note            varchar(4000),
    version         bigint        NOT NULL DEFAULT 0,
    UNIQUE (evaluation_id, round, step_order)
);
