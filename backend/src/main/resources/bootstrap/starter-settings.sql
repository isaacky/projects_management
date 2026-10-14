-- Starter procurement settings for a new organisation, loaded once when the start-up bootstrap
-- creates the tenant (pmp.bootstrap.*). The same placeholder values as the development samples:
-- THEY ARE NOT LEGAL THRESHOLDS. Review and replace them on the Procurement settings page before
-- the first requisition. {{TENANT_ID}} is replaced with the new tenant's id.

-- Methods (amounts in KES cents) -------------------------------------------------
INSERT INTO procurement.methods
    (id, tenant_id, code, name, description, enabled, min_value_minor, max_value_minor,
     currency, min_bid_period_days, requires_public_advert, requires_justification, sort_order, updated_at)
VALUES
    (gen_random_uuid(), '{{TENANT_ID}}',
     'OPEN_TENDER', 'Open tender', 'Advertised competitive tender open to all eligible bidders.',
     true, NULL, NULL, 'KES', 21, true, false, 10, now()),
    (gen_random_uuid(), '{{TENANT_ID}}',
     'RESTRICTED_TENDER', 'Restricted tender', 'Tender to prequalified or shortlisted bidders only.',
     true, NULL, NULL, 'KES', 14, false, true, 20, now()),
    (gen_random_uuid(), '{{TENANT_ID}}',
     'RFQ', 'Request for quotation', 'Quotations from at least three registered suppliers.',
     true, NULL, 300000000, 'KES', 7, false, false, 30, now()),
    (gen_random_uuid(), '{{TENANT_ID}}',
     'DIRECT', 'Direct procurement', 'Single source; only in the circumstances the law allows.',
     true, NULL, NULL, 'KES', 0, false, true, 40, now()),
    (gen_random_uuid(), '{{TENANT_ID}}',
     'FRAMEWORK', 'Framework agreement call-off', 'Call-off order under an existing framework agreement.',
     true, NULL, NULL, 'KES', 0, false, false, 50, now());

-- Approval thresholds (amounts in KES cents) --------------------------------------
-- Requisitions: department head up to KES 5m; above that also the accounting officer.
INSERT INTO procurement.approval_thresholds
    (id, tenant_id, stage, method_code, min_amount_minor, max_amount_minor, currency,
     step_order, approver_role, updated_at)
VALUES
    (gen_random_uuid(), '{{TENANT_ID}}',
     'REQUISITION', NULL, 0, 500000000, 'KES', 1, 'HEAD_OF_DEPARTMENT', now()),
    (gen_random_uuid(), '{{TENANT_ID}}',
     'REQUISITION', NULL, 500000000, NULL, 'KES', 1, 'HEAD_OF_DEPARTMENT', now()),
    (gen_random_uuid(), '{{TENANT_ID}}',
     'REQUISITION', NULL, 500000000, NULL, 'KES', 2, 'ACCOUNTING_OFFICER', now()),
-- Awards: professional opinion from the head of procurement, then the accounting officer.
    (gen_random_uuid(), '{{TENANT_ID}}',
     'AWARD', NULL, 0, NULL, 'KES', 1, 'HEAD_OF_PROCUREMENT', now()),
    (gen_random_uuid(), '{{TENANT_ID}}',
     'AWARD', NULL, 0, NULL, 'KES', 2, 'ACCOUNTING_OFFICER', now()),
-- RFQ awards up to KES 3m: head of procurement alone (method-specific override).
    (gen_random_uuid(), '{{TENANT_ID}}',
     'AWARD', 'RFQ', 0, 300000000, 'KES', 1, 'HEAD_OF_PROCUREMENT', now()),
-- Contract variations: accounting officer.
    (gen_random_uuid(), '{{TENANT_ID}}',
     'CONTRACT_VARIATION', NULL, 0, NULL, 'KES', 1, 'ACCOUNTING_OFFICER', now());

-- Evaluation committees -------------------------------------------------------------
INSERT INTO procurement.evaluation_committee_rules
    (id, tenant_id, method_code, min_members, max_members, quorum, two_envelope,
     technical_pass_mark_pct, requires_coi_declaration, report_signatories,
     report_reviewer_role, award_approver_role, updated_at)
VALUES
    -- Default for any method without its own rule.
    (gen_random_uuid(), '{{TENANT_ID}}',
     NULL, 3, 5, 3, false, NULL, true, 'ALL_MEMBERS',
     'HEAD_OF_PROCUREMENT', 'ACCOUNTING_OFFICER', now()),
    -- Open tenders: technical and financial bids opened separately.
    (gen_random_uuid(), '{{TENANT_ID}}',
     'OPEN_TENDER', 5, 7, 4, true, 70, true, 'ALL_MEMBERS',
     'HEAD_OF_PROCUREMENT', 'ACCOUNTING_OFFICER', now()),
    -- RFQs: small committee, chair and secretary sign.
    (gen_random_uuid(), '{{TENANT_ID}}',
     'RFQ', 3, 3, 3, false, NULL, true, 'CHAIR_AND_SECRETARY',
     'HEAD_OF_PROCUREMENT', 'HEAD_OF_PROCUREMENT', now());

-- Evaluation templates -------------------------------------------------------------
INSERT INTO procurement.evaluation_templates
    (id, tenant_id, code, name, method_code, description, active, updated_at)
VALUES
    (gen_random_uuid(), '{{TENANT_ID}}',
     'OPEN_TENDER_STANDARD', 'Open tender (goods and works)', 'OPEN_TENDER',
     'Mandatory requirements, then technical scoring out of 100.', true, now()),
    (gen_random_uuid(), '{{TENANT_ID}}',
     'RFQ_STANDARD', 'Request for quotation', 'RFQ',
     'Mandatory requirements only; the lowest compliant quotation wins.', true, now()),
    (gen_random_uuid(), '{{TENANT_ID}}',
     'GENERAL', 'General (any method)', NULL,
     'Basic mandatory requirements; add technical criteria as needed.', true, now());

INSERT INTO procurement.evaluation_template_criteria
    (template_id, sort_order, stage, name, description, max_score)
SELECT t.id, v.sort_order, v.stage, v.name, v.description, v.max_score
FROM (VALUES
    -- Open tender: preliminary (mandatory, pass/fail)
    ('OPEN_TENDER_STANDARD', 1, 'PRELIMINARY', 'Certificate of incorporation or registration', NULL, NULL),
    ('OPEN_TENDER_STANDARD', 2, 'PRELIMINARY', 'Valid KRA tax compliance certificate', NULL, NULL),
    ('OPEN_TENDER_STANDARD', 3, 'PRELIMINARY', 'Tender security of the required form and amount', NULL, NULL),
    ('OPEN_TENDER_STANDARD', 4, 'PRELIMINARY', 'Form of tender duly completed and signed', NULL, NULL),
    ('OPEN_TENDER_STANDARD', 5, 'PRELIMINARY', 'Confidential business questionnaire', NULL, NULL),
    ('OPEN_TENDER_STANDARD', 6, 'PRELIMINARY', 'Signed self-declaration (not debarred, no conflict of interest)', NULL, NULL),
    -- Open tender: technical (scored)
    ('OPEN_TENDER_STANDARD', 7, 'TECHNICAL', 'Experience in similar contracts', 'Contracts of similar size and nature in the last 5 years', 20),
    ('OPEN_TENDER_STANDARD', 8, 'TECHNICAL', 'Key personnel', 'Qualifications and experience of the proposed team', 20),
    ('OPEN_TENDER_STANDARD', 9, 'TECHNICAL', 'Equipment and facilities', NULL, 15),
    ('OPEN_TENDER_STANDARD', 10, 'TECHNICAL', 'Methodology and work plan', NULL, 25),
    ('OPEN_TENDER_STANDARD', 11, 'TECHNICAL', 'Financial capability', 'Audited accounts for the last 2 years, bank statements', 20),
    -- RFQ
    ('RFQ_STANDARD', 1, 'PRELIMINARY', 'Valid KRA tax compliance certificate', NULL, NULL),
    ('RFQ_STANDARD', 2, 'PRELIMINARY', 'Quotation signed and stamped', NULL, NULL),
    ('RFQ_STANDARD', 3, 'PRELIMINARY', 'Meets the specification', NULL, NULL),
    -- General
    ('GENERAL', 1, 'PRELIMINARY', 'Certificate of incorporation or registration', NULL, NULL),
    ('GENERAL', 2, 'PRELIMINARY', 'Valid KRA tax compliance certificate', NULL, NULL),
    ('GENERAL', 3, 'PRELIMINARY', 'Form of tender duly completed and signed', NULL, NULL)
) AS v (code, sort_order, stage, name, description, max_score)
JOIN procurement.evaluation_templates t ON t.code = v.code AND t.tenant_id = '{{TENANT_ID}}';
