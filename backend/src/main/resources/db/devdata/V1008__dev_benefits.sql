-- Development sample: benefits registers for two projects, with some measurements, so the
-- Benefits tab and the portfolio page have something to show. Owner: Sample Operations Officer.

INSERT INTO benefits.benefits
    (id, tenant_id, project_id, number, title, description, category, objective, owner_id, measure, unit,
     direction, baseline_value, baseline_date, target_value, target_date, frequency, annual_value_minor, status,
     created_by, created_at, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000009001', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000001003', 1, 'Fewer estimated bills',
     'Smart meters are read remotely, so customers in the pilot area get bills from actual readings.',
     'CUSTOMER', 'Accurate billing for prepaid and postpaid customers in the pilot area',
     '01920000-0000-7000-8000-000000000114', 'Share of bills based on estimates', '%', 'DECREASE',
     38, '2026-01-01', 5, '2027-06-30', 'QUARTERLY', NULL, 'ACTIVE',
     '01920000-0000-7000-8000-000000000102', now(), now()),
    ('01920000-0000-7000-8000-000000009002', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000001003', 2, 'Lower commercial losses',
     'Tamper alerts and remote disconnection reduce energy lost to theft and meter bypass.',
     'FINANCIAL', 'Reduce system losses in line with the regulator''s loss target',
     '01920000-0000-7000-8000-000000000114', 'Commercial losses in the pilot area', '% of energy', 'DECREASE',
     9.5, '2026-01-01', 6, '2027-12-31', 'QUARTERLY', 42000000000, 'ACTIVE',
     '01920000-0000-7000-8000-000000000102', now(), now()),
    ('01920000-0000-7000-8000-000000009003', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000001001', 1, 'New customers connected',
     'Households and businesses connected along the new lines in lot A.',
     'CUSTOMER', 'Universal access to electricity',
     '01920000-0000-7000-8000-000000000114', 'Customers connected', 'customers', 'INCREASE',
     0, '2026-07-01', 12000, '2028-06-30', 'MONTHLY', 30000000000, 'ACTIVE',
     '01920000-0000-7000-8000-000000000102', now(), now());

INSERT INTO benefits.measurements
    (id, tenant_id, benefit_id, measured_on, value, source, note, recorded_by, recorded_at, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000009101', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000009001', '2026-04-01', 31, 'Billing system report BR-12', NULL,
     '01920000-0000-7000-8000-000000000114', now(), now()),
    ('01920000-0000-7000-8000-000000009102', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000009001', '2026-07-01', 24, 'Billing system report BR-12', NULL,
     '01920000-0000-7000-8000-000000000114', now(), now()),
    ('01920000-0000-7000-8000-000000009103', '01920000-0000-7000-8000-000000000001',
     '01920000-0000-7000-8000-000000009002', '2026-07-01', 9.1, 'Energy balance, pilot feeders',
     'Tamper alerts only active on half the meters so far.', '01920000-0000-7000-8000-000000000114', now(), now());
