-- Development sample procurement settings for the "Kenya Power (pilot)" tenant.
-- THESE ARE PLACEHOLDER VALUES, NOT LEGAL THRESHOLDS. Replace them with the figures
-- Kenya Power confirms (and that the PPADA 2015 regulations require) through the
-- Procurement settings page or the API.

-- Methods (amounts in KES cents) -------------------------------------------------
INSERT INTO procurement.methods
    (id, tenant_id, code, name, description, enabled, min_value_minor, max_value_minor,
     currency, min_bid_period_days, requires_public_advert, requires_justification, sort_order, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000002001', '01920000-0000-7000-8000-000000000001',
     'OPEN_TENDER', 'Open tender', 'Advertised competitive tender open to all eligible bidders.',
     true, NULL, NULL, 'KES', 21, true, false, 10, now()),
    ('01920000-0000-7000-8000-000000002002', '01920000-0000-7000-8000-000000000001',
     'RESTRICTED_TENDER', 'Restricted tender', 'Tender to prequalified or shortlisted bidders only.',
     true, NULL, NULL, 'KES', 14, false, true, 20, now()),
    ('01920000-0000-7000-8000-000000002003', '01920000-0000-7000-8000-000000000001',
     'RFQ', 'Request for quotation', 'Quotations from at least three registered suppliers.',
     true, NULL, 300000000, 'KES', 7, false, false, 30, now()),
    ('01920000-0000-7000-8000-000000002004', '01920000-0000-7000-8000-000000000001',
     'DIRECT', 'Direct procurement', 'Single source; only in the circumstances the law allows.',
     true, NULL, NULL, 'KES', 0, false, true, 40, now()),
    ('01920000-0000-7000-8000-000000002005', '01920000-0000-7000-8000-000000000001',
     'FRAMEWORK', 'Framework agreement call-off', 'Call-off order under an existing framework agreement.',
     true, NULL, NULL, 'KES', 0, false, false, 50, now());

-- Approval thresholds (amounts in KES cents) --------------------------------------
-- Requisitions: department head up to KES 5m; above that also the accounting officer.
INSERT INTO procurement.approval_thresholds
    (id, tenant_id, stage, method_code, min_amount_minor, max_amount_minor, currency,
     step_order, approver_role, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000003001', '01920000-0000-7000-8000-000000000001',
     'REQUISITION', NULL, 0, 500000000, 'KES', 1, 'HEAD_OF_DEPARTMENT', now()),
    ('01920000-0000-7000-8000-000000003002', '01920000-0000-7000-8000-000000000001',
     'REQUISITION', NULL, 500000000, NULL, 'KES', 1, 'HEAD_OF_DEPARTMENT', now()),
    ('01920000-0000-7000-8000-000000003003', '01920000-0000-7000-8000-000000000001',
     'REQUISITION', NULL, 500000000, NULL, 'KES', 2, 'ACCOUNTING_OFFICER', now()),
-- Awards: professional opinion from the head of procurement, then the accounting officer.
    ('01920000-0000-7000-8000-000000003004', '01920000-0000-7000-8000-000000000001',
     'AWARD', NULL, 0, NULL, 'KES', 1, 'HEAD_OF_PROCUREMENT', now()),
    ('01920000-0000-7000-8000-000000003005', '01920000-0000-7000-8000-000000000001',
     'AWARD', NULL, 0, NULL, 'KES', 2, 'ACCOUNTING_OFFICER', now()),
-- RFQ awards up to KES 3m: head of procurement alone (method-specific override).
    ('01920000-0000-7000-8000-000000003006', '01920000-0000-7000-8000-000000000001',
     'AWARD', 'RFQ', 0, 300000000, 'KES', 1, 'HEAD_OF_PROCUREMENT', now()),
-- Contract variations: accounting officer.
    ('01920000-0000-7000-8000-000000003007', '01920000-0000-7000-8000-000000000001',
     'CONTRACT_VARIATION', NULL, 0, NULL, 'KES', 1, 'ACCOUNTING_OFFICER', now());

-- Evaluation committees -------------------------------------------------------------
INSERT INTO procurement.evaluation_committee_rules
    (id, tenant_id, method_code, min_members, max_members, quorum, two_envelope,
     technical_pass_mark_pct, requires_coi_declaration, report_signatories,
     report_reviewer_role, award_approver_role, updated_at)
VALUES
    -- Default for any method without its own rule.
    ('01920000-0000-7000-8000-000000004001', '01920000-0000-7000-8000-000000000001',
     NULL, 3, 5, 3, false, NULL, true, 'ALL_MEMBERS',
     'HEAD_OF_PROCUREMENT', 'ACCOUNTING_OFFICER', now()),
    -- Open tenders: technical and financial bids opened separately.
    ('01920000-0000-7000-8000-000000004002', '01920000-0000-7000-8000-000000000001',
     'OPEN_TENDER', 5, 7, 4, true, 70, true, 'ALL_MEMBERS',
     'HEAD_OF_PROCUREMENT', 'ACCOUNTING_OFFICER', now()),
    -- RFQs: small committee, chair and secretary sign.
    ('01920000-0000-7000-8000-000000004003', '01920000-0000-7000-8000-000000000001',
     'RFQ', 3, 3, 3, false, NULL, true, 'CHAIR_AND_SECRETARY',
     'HEAD_OF_PROCUREMENT', 'HEAD_OF_PROCUREMENT', now());
