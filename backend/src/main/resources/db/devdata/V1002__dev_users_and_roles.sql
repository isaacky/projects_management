-- Development sample users and their roles, so approval chains can be tried end to end
-- with the "Viewing as" switcher in the web app. Names are placeholders.

INSERT INTO platform.users (id, tenant_id, email, display_name)
VALUES
    ('01920000-0000-7000-8000-000000000102', '01920000-0000-7000-8000-000000000001',
     'requester@example.com', 'Sample Requester (Project Manager)'),
    ('01920000-0000-7000-8000-000000000103', '01920000-0000-7000-8000-000000000001',
     'hod@example.com', 'Sample Head of Department'),
    ('01920000-0000-7000-8000-000000000104', '01920000-0000-7000-8000-000000000001',
     'hop@example.com', 'Sample Head of Procurement'),
    ('01920000-0000-7000-8000-000000000105', '01920000-0000-7000-8000-000000000001',
     'ao@example.com', 'Sample Accounting Officer');

INSERT INTO platform.user_roles (user_id, role_code)
VALUES
    ('01920000-0000-7000-8000-000000000101', 'PMO_LEAD'),
    ('01920000-0000-7000-8000-000000000102', 'PROJECT_MANAGER'),
    ('01920000-0000-7000-8000-000000000103', 'HEAD_OF_DEPARTMENT'),
    ('01920000-0000-7000-8000-000000000104', 'HEAD_OF_PROCUREMENT'),
    ('01920000-0000-7000-8000-000000000105', 'ACCOUNTING_OFFICER');
