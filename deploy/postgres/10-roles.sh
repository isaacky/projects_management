#!/bin/sh
# Runs once, when the database volume is first created (docker-entrypoint-initdb.d).
# Creates the restricted roles the platform connects as, and Keycloak's own database.
#   pmp            owner of the platform schemas (POSTGRES_USER); used only for migrations and backups
#   pmp_app        the backend: data access only, subject to row-level security
#   pmp_analytics  the analytics service: read only, subject to row-level security
#   keycloak       owns the separate "keycloak" database
# Grants and policies on the tables are applied by the backend's migrations at start-up.
set -eu

secret() { tr -d '\r\n' < "/run/secrets/$1"; }

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_pw="$(secret PMP_DB_APP_PASSWORD)" \
  -v analytics_pw="$(secret PMP_DB_ANALYTICS_PASSWORD)" \
  -v kc_pw="$(secret KC_DB_PASSWORD)" <<'SQL'
CREATE ROLE pmp_app LOGIN PASSWORD :'app_pw';
CREATE ROLE pmp_analytics LOGIN PASSWORD :'analytics_pw';
CREATE ROLE keycloak LOGIN PASSWORD :'kc_pw';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE pmp FROM PUBLIC;
GRANT CONNECT ON DATABASE pmp TO pmp_app, pmp_analytics;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL
echo "Created roles pmp_app, pmp_analytics and the keycloak database"
