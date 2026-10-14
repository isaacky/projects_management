-- Development sample: the 2026/27 annual procurement plan, still being prepared.
-- Network Operations (head: Sample Head of Department) has submitted its lines; ICT (head:
-- Sample Requester) is still drafting; Corporate Services (head: PMO Lead) has not started.
-- It is left unapproved on purpose: an approved plan changes how requisitions are submitted.
-- Amounts in KES cents.

INSERT INTO procurement.plans
    (id, tenant_id, financial_year, fy_start, fy_end, currency, status, revision, next_line_no,
     agpo_target_pct, created_by, created_at, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000007001', '01920000-0000-7000-8000-000000000001',
     '2026/27', '2026-07-01', '2027-06-30', 'KES', 'DRAFT', 0, 8, 30,
     '01920000-0000-7000-8000-000000000104', now(), now());

INSERT INTO procurement.plan_departments
    (id, tenant_id, plan_id, name, head_id, ceiling_minor, status, submitted_by, submitted_at, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000007101', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', 'Network Operations', '01920000-0000-7000-8000-000000000103',
     100000000000, 'SUBMITTED', '01920000-0000-7000-8000-000000000103', now(), now()),
    ('01920000-0000-7000-8000-000000007102', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', 'ICT', '01920000-0000-7000-8000-000000000102',
     1500000000, 'DRAFT', NULL, NULL, now()),
    ('01920000-0000-7000-8000-000000007103', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', 'Corporate Services', '01920000-0000-7000-8000-000000000101',
     NULL, 'DRAFT', NULL, NULL, now());

INSERT INTO procurement.plan_lines
    (id, tenant_id, plan_id, department_id, line_no, description, category, quantity, unit, estimate_minor,
     method_code, quarter, source_of_funds, budget_code, reserved_group, project_id, created_by, created_at,
     updated_at)
VALUES
    ('01920000-0000-7000-8000-000000007201', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', '01920000-0000-7000-8000-000000007101', 1,
     'Supply of 100 kVA distribution transformers', 'GOODS', 120, 'each', 36000000000,
     'OPEN_TENDER', 2, 'GoK development', 'D-2110', NULL, '01920000-0000-7000-8000-000000001001',
     '01920000-0000-7000-8000-000000000103', now(), now()),
    ('01920000-0000-7000-8000-000000007202', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', '01920000-0000-7000-8000-000000007101', 2,
     'Construction of 33 kV line, sample lot A', 'WORKS', 45, 'km', 54000000000,
     'OPEN_TENDER', 2, 'GoK development', 'D-2120', NULL, '01920000-0000-7000-8000-000000001001',
     '01920000-0000-7000-8000-000000000103', now(), now()),
    ('01920000-0000-7000-8000-000000007203', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', '01920000-0000-7000-8000-000000007101', 3,
     'Wayleave survey and valuation consultancy', 'CONSULTANCY', 1, 'lot', 1800000000,
     'RESTRICTED_TENDER', 1, 'GoK development', 'D-2130', NULL, NULL,
     '01920000-0000-7000-8000-000000000103', now(), now()),
    ('01920000-0000-7000-8000-000000007204', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', '01920000-0000-7000-8000-000000007101', 4,
     'Meter boxes', 'GOODS', 5000, 'each', 1250000000,
     'OPEN_TENDER', 3, 'Internal resources', 'R-3105', 'YOUTH', NULL,
     '01920000-0000-7000-8000-000000000103', now(), now()),
    ('01920000-0000-7000-8000-000000007205', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', '01920000-0000-7000-8000-000000007101', 5,
     'Bush clearing along distribution lines', 'NON_CONSULTING', 300, 'km', 240000000,
     'RFQ', 2, 'Internal resources', 'R-3140', 'WOMEN', NULL,
     '01920000-0000-7000-8000-000000000103', now(), now()),
    ('01920000-0000-7000-8000-000000007206', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', '01920000-0000-7000-8000-000000007102', 6,
     'Laptops for field engineers', 'GOODS', 80, 'each', 960000000,
     'OPEN_TENDER', 1, 'Internal resources', 'R-4110', 'AGPO', NULL,
     '01920000-0000-7000-8000-000000000102', now(), now()),
    ('01920000-0000-7000-8000-000000007207', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000007001', '01920000-0000-7000-8000-000000007102', 7,
     'Network switch maintenance', 'NON_CONSULTING', 1, 'lot', 280000000,
     'RFQ', 3, 'Internal resources', 'R-4150', NULL, NULL,
     '01920000-0000-7000-8000-000000000102', now(), now());
