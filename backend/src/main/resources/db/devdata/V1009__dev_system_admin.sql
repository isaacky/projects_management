-- The sample PMO lead also administers users and roles (Administration > Users & roles).
INSERT INTO platform.user_roles (user_id, role_code)
VALUES ('01920000-0000-7000-8000-000000000101', 'SYSTEM_ADMIN');

-- Development only: let the backend connect as the restricted application role, as on a server, so
-- row-level security applies locally too (the dev,sso profiles do this). Password pmp_app.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pmp_app') THEN
        CREATE ROLE pmp_app LOGIN PASSWORD 'pmp_app';
    ELSE
        ALTER ROLE pmp_app LOGIN PASSWORD 'pmp_app';
    END IF;
END
$$;
