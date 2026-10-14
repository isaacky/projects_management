-- Development sample: evaluators and evaluation criteria templates.
-- The criteria are typical of Kenyan public tenders but are placeholders: replace them with
-- Kenya Power's standard tender document criteria in Procurement settings > Evaluation criteria.

INSERT INTO platform.users (id, tenant_id, email, display_name)
VALUES
    ('01920000-0000-7000-8000-000000000107', '01920000-0000-7000-8000-000000000001',
     'eval.engineering@example.com', 'Sample Evaluator (Engineering)'),
    ('01920000-0000-7000-8000-000000000108', '01920000-0000-7000-8000-000000000001',
     'eval.finance@example.com', 'Sample Evaluator (Finance)'),
    ('01920000-0000-7000-8000-000000000109', '01920000-0000-7000-8000-000000000001',
     'eval.legal@example.com', 'Sample Evaluator (Legal)'),
    ('01920000-0000-7000-8000-000000000110', '01920000-0000-7000-8000-000000000001',
     'eval.planning@example.com', 'Sample Evaluator (Network Planning)'),
    ('01920000-0000-7000-8000-000000000111', '01920000-0000-7000-8000-000000000001',
     'eval.supplychain@example.com', 'Sample Evaluator (Supply Chain)');

INSERT INTO procurement.evaluation_templates
    (id, tenant_id, code, name, method_code, description, active, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000006001', '01920000-0000-7000-8000-000000000001',
     'OPEN_TENDER_STANDARD', 'Open tender (goods and works)', 'OPEN_TENDER',
     'Mandatory requirements, then technical scoring out of 100.', true, now()),
    ('01920000-0000-7000-8000-000000006002', '01920000-0000-7000-8000-000000000001',
     'RFQ_STANDARD', 'Request for quotation', 'RFQ',
     'Mandatory requirements only; the lowest compliant quotation wins.', true, now()),
    ('01920000-0000-7000-8000-000000006003', '01920000-0000-7000-8000-000000000001',
     'GENERAL', 'General (any method)', NULL,
     'Basic mandatory requirements; add technical criteria as needed.', true, now());

INSERT INTO procurement.evaluation_template_criteria
    (template_id, sort_order, stage, name, description, max_score)
VALUES
    -- Open tender: preliminary (mandatory, pass/fail)
    ('01920000-0000-7000-8000-000000006001', 1, 'PRELIMINARY', 'Certificate of incorporation or registration', NULL, NULL),
    ('01920000-0000-7000-8000-000000006001', 2, 'PRELIMINARY', 'Valid KRA tax compliance certificate', NULL, NULL),
    ('01920000-0000-7000-8000-000000006001', 3, 'PRELIMINARY', 'Tender security of the required form and amount', NULL, NULL),
    ('01920000-0000-7000-8000-000000006001', 4, 'PRELIMINARY', 'Form of tender duly completed and signed', NULL, NULL),
    ('01920000-0000-7000-8000-000000006001', 5, 'PRELIMINARY', 'Confidential business questionnaire', NULL, NULL),
    ('01920000-0000-7000-8000-000000006001', 6, 'PRELIMINARY', 'Signed self-declaration (not debarred, no conflict of interest)', NULL, NULL),
    -- Open tender: technical (scored)
    ('01920000-0000-7000-8000-000000006001', 7, 'TECHNICAL', 'Experience in similar contracts', 'Contracts of similar size and nature in the last 5 years', 20),
    ('01920000-0000-7000-8000-000000006001', 8, 'TECHNICAL', 'Key personnel', 'Qualifications and experience of the proposed team', 20),
    ('01920000-0000-7000-8000-000000006001', 9, 'TECHNICAL', 'Equipment and facilities', NULL, 15),
    ('01920000-0000-7000-8000-000000006001', 10, 'TECHNICAL', 'Methodology and work plan', NULL, 25),
    ('01920000-0000-7000-8000-000000006001', 11, 'TECHNICAL', 'Financial capability', 'Audited accounts for the last 2 years, bank statements', 20),
    -- RFQ
    ('01920000-0000-7000-8000-000000006002', 1, 'PRELIMINARY', 'Valid KRA tax compliance certificate', NULL, NULL),
    ('01920000-0000-7000-8000-000000006002', 2, 'PRELIMINARY', 'Quotation signed and stamped', NULL, NULL),
    ('01920000-0000-7000-8000-000000006002', 3, 'PRELIMINARY', 'Meets the specification', NULL, NULL),
    -- General
    ('01920000-0000-7000-8000-000000006003', 1, 'PRELIMINARY', 'Certificate of incorporation or registration', NULL, NULL),
    ('01920000-0000-7000-8000-000000006003', 2, 'PRELIMINARY', 'Valid KRA tax compliance certificate', NULL, NULL),
    ('01920000-0000-7000-8000-000000006003', 3, 'PRELIMINARY', 'Form of tender duly completed and signed', NULL, NULL);
