-- Development sample: an operations officer to receive handovers, and two lessons learned so the
-- library is not empty. The completion checklist uses the built-in list until the PMO sets one up.

INSERT INTO platform.users (id, tenant_id, email, display_name)
VALUES
    ('01920000-0000-7000-8000-000000000114', '01920000-0000-7000-8000-000000000001',
     'operations@example.com', 'Sample Operations Officer');

INSERT INTO closeout.lessons
    (id, tenant_id, project_id, kind, category, phase, title, what_happened, recommendation, status,
     created_by, created_at, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000008001', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000001003', 'IMPROVE', 'WAYLEAVE', 'EXECUTION',
     'Start wayleave consents before the contract is signed',
     'Consents for 14 of 60 sites were still outstanding when the contractor mobilised, idling crews for three weeks.',
     'Make wayleave consent for at least 80% of sites a condition of gate G1.', 'SUBMITTED',
     '01920000-0000-7000-8000-000000000102', now(), now()),
    ('01920000-0000-7000-8000-000000008002', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000001003', 'WENT_WELL', 'CONTRACTOR', 'EXECUTION',
     'Weekly joint site walks with the contractor',
     'A short weekly walk with the contractor''s site agent caught installation errors before they were covered up.',
     'Write weekly joint site walks into the contract management plan for works contracts.', 'SUBMITTED',
     '01920000-0000-7000-8000-000000000102', now(), now());
