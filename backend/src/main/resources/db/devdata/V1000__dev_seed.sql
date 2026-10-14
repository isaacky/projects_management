-- Development sample data (dev profile only). Never runs in staging or production.

INSERT INTO platform.tenants (id, slug, name)
VALUES ('01920000-0000-7000-8000-000000000001', 'kplc-pilot', 'Kenya Power (pilot)');

INSERT INTO platform.users (id, tenant_id, email, display_name)
VALUES ('01920000-0000-7000-8000-000000000101', '01920000-0000-7000-8000-000000000001',
        'pmo.lead@example.com', 'PMO Lead (sample)');

-- Sample projects across the lifecycle. Amounts are in KES cents.
INSERT INTO portfolio.projects
    (id, tenant_id, code, name, description, sponsor, phase, status,
     budget_minor, currency, created_at, updated_at)
VALUES
    ('01920000-0000-7000-8000-000000001001', '01920000-0000-7000-8000-000000000001',
     'NET-2026-001', 'Rural network extension, sample lot A',
     'Sample project for local development.', 'Sample sponsor',
     'PROCUREMENT', 'ACTIVE', 125000000000, 'KES', now(), now()),
    ('01920000-0000-7000-8000-000000001002', '01920000-0000-7000-8000-000000000001',
     'SUB-2026-004', 'Substation upgrade, sample site',
     'Sample project for local development.', 'Sample sponsor',
     'PLANNING', 'ACTIVE', 480000000000, 'KES', now(), now()),
    ('01920000-0000-7000-8000-000000001003', '01920000-0000-7000-8000-000000000001',
     'MTR-2026-002', 'Smart metering rollout, sample phase 1',
     'Sample project for local development.', 'Sample sponsor',
     'EXECUTION', 'ACTIVE', 92000000000, 'KES', now(), now()),
    ('01920000-0000-7000-8000-000000001004', '01920000-0000-7000-8000-000000000001',
     'ICT-2026-007', 'Field workforce system, sample',
     'Sample project for local development.', 'Sample sponsor',
     'INITIATION', 'ACTIVE', 15000000000, 'KES', now(), now());
