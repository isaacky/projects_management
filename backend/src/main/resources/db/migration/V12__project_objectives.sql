-- V12: project objectives, and their assessment in post-implementation reviews.
--
-- A project lists what it is meant to achieve when it is created (from the business case). The
-- list can be refined freely during initiation; after gate G0 only the PMO may change it, with a
-- reason, and an objective is withdrawn rather than deleted so the record stays complete. Each
-- post-implementation review then records, per objective, whether it was met, with evidence; the
-- review's overall "objectives met" follows from those outcomes.

CREATE TABLE portfolio.project_objectives (
    id                uuid PRIMARY KEY,
    tenant_id         uuid          NOT NULL REFERENCES platform.tenants (id),
    project_id        uuid          NOT NULL REFERENCES portfolio.projects (id),
    number            integer       NOT NULL CHECK (number >= 1),
    statement         varchar(500)  NOT NULL,
    -- How the review will tell whether it was met, e.g. "12,000 customers connected by June 2028".
    success_measure   varchar(500),
    status            varchar(10)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    withdrawn_reason  varchar(2000),
    created_by        uuid          REFERENCES platform.users (id),
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    version           bigint        NOT NULL DEFAULT 0,
    UNIQUE (project_id, number),
    CHECK (status = 'ACTIVE' OR withdrawn_reason IS NOT NULL)
);
CREATE INDEX project_objectives_tenant_project ON portfolio.project_objectives (tenant_id, project_id);

CREATE TABLE benefits.review_objective_assessments (
    id            uuid PRIMARY KEY,
    review_id     uuid          NOT NULL REFERENCES benefits.reviews (id) ON DELETE CASCADE,
    objective_id  uuid          NOT NULL REFERENCES portfolio.project_objectives (id),
    outcome       varchar(10)   NOT NULL CHECK (outcome IN ('MET', 'PARTLY_MET', 'NOT_MET', 'TOO_EARLY')),
    evidence      varchar(2000),
    -- The objective as worded when the review was completed, so later edits do not change the record.
    statement     varchar(500),
    version       bigint        NOT NULL DEFAULT 0,
    UNIQUE (review_id, objective_id)
);
CREATE INDEX review_objective_assessments_objective ON benefits.review_objective_assessments (objective_id);
