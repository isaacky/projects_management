-- Development sample: a contract manager and a finance officer for the contracts module.

INSERT INTO platform.users (id, tenant_id, email, display_name)
VALUES
    ('01920000-0000-7000-8000-000000000112', '01920000-0000-7000-8000-000000000001',
     'contracts@example.com', 'Sample Contract Manager'),
    ('01920000-0000-7000-8000-000000000113', '01920000-0000-7000-8000-000000000001',
     'finance@example.com', 'Sample Finance Officer');

INSERT INTO platform.user_roles (user_id, role_code)
VALUES
    ('01920000-0000-7000-8000-000000000112', 'CONTRACT_MANAGER'),
    ('01920000-0000-7000-8000-000000000113', 'FINANCE_OFFICER');
