-- Row-level security and database roles. A Flyway callback, not a versioned migration: it runs
-- after every migrate (including when nothing new was applied) and is safe to repeat, so every
-- table that has a tenant_id column -- including ones added by future migrations -- is covered.
--
-- Roles
--   owner (the migration user)  owns the schemas and tables; not subject to the policies.
--   ${app_role}                 the backend at run time: read and write data, nothing else.
--   ${analytics_role}           the analytics service: read only.
-- On servers both are created with passwords by deploy/postgres/initdb; where they are missing
-- (a fresh local database) they are created here without login, so the grants below still apply.
--
-- Policies
--   Each connection says which tenant it works for (set_config('app.tenant_id', ...), done by the
--   backend for every connection it takes from its pool and by analytics per request). Rows of other
--   tenants are invisible and cannot be written. With no tenant set, no tenant-scoped rows are
--   visible at all.
--   * Tables with a tenant_id column: the row's tenant_id must be the connection's tenant.
--   * Child tables without one (bid files, evaluation scores, securities, plan lines, ...): the row's
--     parent, found through a NOT NULL foreign key, must be visible -- which applies the parent's
--     own policy, all the way up to a table with a tenant_id.
--   Tables reachable neither way are listed in a NOTICE (the platform's own tenants table is the only
--   one expected).

DO $rls$
DECLARE
    schemas   text[] := ARRAY['platform', 'portfolio', 'procurement', 'contracts', 'planning',
                              'execution', 'closeout', 'benefits'];
    roles     text[] := ARRAY['${app_role}', '${analytics_role}'];
    may_create boolean;
    r         text;
    s         text;
    t         record;
    k_row     record;
    cond      text;
    progress  boolean;
    isolation text := 'tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid';
BEGIN
    SELECT rolsuper OR rolcreaterole INTO may_create FROM pg_roles WHERE rolname = current_user;
    FOREACH r IN ARRAY roles LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            IF may_create THEN
                EXECUTE format('CREATE ROLE %I NOLOGIN', r);
            ELSE
                RAISE NOTICE 'Role % does not exist and % may not create it; skipping its grants', r, current_user;
            END IF;
        END IF;
    END LOOP;

    FOR t IN
        SELECT c.table_schema AS sch, c.table_name AS tbl
        FROM information_schema.columns c
        JOIN information_schema.tables x ON x.table_schema = c.table_schema AND x.table_name = c.table_name
        WHERE c.column_name = 'tenant_id' AND x.table_type = 'BASE TABLE' AND c.table_schema = ANY (schemas)
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', t.sch, t.tbl);
        IF NOT EXISTS (SELECT 1 FROM pg_policies
                       WHERE schemaname = t.sch AND tablename = t.tbl AND policyname = 'tenant_isolation') THEN
            EXECUTE format('CREATE POLICY tenant_isolation ON %I.%I USING (%s) WITH CHECK (%s)',
                           t.sch, t.tbl, isolation, isolation);
        END IF;
    END LOOP;

    -- Child tables, outward from the tenant-scoped ones until nothing changes.
    LOOP
        progress := false;
        FOR k_row IN
            SELECT n.nspname AS sch, cl.relname AS tbl, a.attname AS col,
                   pn.nspname AS psch, pc.relname AS ptbl, pa.attname AS pcol, cl.oid AS child
            FROM pg_constraint k
            JOIN pg_class cl ON cl.oid = k.conrelid
            JOIN pg_namespace n ON n.oid = cl.relnamespace
            JOIN pg_class pc ON pc.oid = k.confrelid
            JOIN pg_namespace pn ON pn.oid = pc.relnamespace
            JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = k.conkey[1]
            JOIN pg_attribute pa ON pa.attrelid = k.confrelid AND pa.attnum = k.confkey[1]
            WHERE k.contype = 'f' AND array_length(k.conkey, 1) = 1 AND a.attnotnull
              AND cl.relkind = 'r' AND n.nspname = ANY (schemas)
              AND cl.oid <> pc.oid AND pc.relrowsecurity AND NOT cl.relrowsecurity
              AND NOT EXISTS (SELECT 1 FROM pg_attribute x
                              WHERE x.attrelid = cl.oid AND x.attname = 'tenant_id' AND NOT x.attisdropped)
            ORDER BY n.nspname, cl.relname, k.conname
        LOOP
            CONTINUE WHEN (SELECT relrowsecurity FROM pg_class WHERE oid = k_row.child); -- done via another key
            cond := format('EXISTS (SELECT 1 FROM %I.%I p WHERE p.%I = %I.%I)', k_row.psch, k_row.ptbl, k_row.pcol, k_row.tbl, k_row.col);
            EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', k_row.sch, k_row.tbl);
            EXECUTE format('CREATE POLICY tenant_isolation ON %I.%I USING (%s) WITH CHECK (%s)',
                           k_row.sch, k_row.tbl, cond, cond);
            progress := true;
        END LOOP;
        EXIT WHEN NOT progress;
    END LOOP;

    FOR t IN
        SELECT n.nspname AS sch, cl.relname AS tbl
        FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace
        WHERE cl.relkind = 'r' AND n.nspname = ANY (schemas) AND NOT cl.relrowsecurity
          AND cl.relname NOT IN ('tenants', 'flyway_schema_history')
    LOOP
        RAISE NOTICE 'No row-level security on %.% (no tenant_id and no required parent)', t.sch, t.tbl;
    END LOOP;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}') THEN
        FOREACH s IN ARRAY schemas LOOP
            EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', s, '${app_role}');
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO %I', s, '${app_role}');
            EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO %I', s, '${app_role}');
        END LOOP;
        -- The audit log is append-only, and the migration history belongs to the owner.
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON platform.audit_events FROM %I', '${app_role}');
        EXECUTE format('REVOKE ALL ON platform.flyway_schema_history FROM %I', '${app_role}');
    END IF;

    -- Analytics reads project data, not the platform schema (people, roles, audit, notifications).
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${analytics_role}') THEN
        FOREACH s IN ARRAY schemas LOOP
            CONTINUE WHEN s = 'platform';
            EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', s, '${analytics_role}');
            EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA %I TO %I', s, '${analytics_role}');
        END LOOP;
        EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA platform FROM %I', '${analytics_role}');
        EXECUTE format('REVOKE USAGE ON SCHEMA platform FROM %I', '${analytics_role}');
    END IF;
END
$rls$;
