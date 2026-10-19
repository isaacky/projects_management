# PM Platform pilot: runbook

How to install, upgrade, back up and restore the platform on Docker Swarm, and how sign-in works.
Everything here runs on the Swarm manager unless it says otherwise.

| Part | What it is |
|---|---|
| `stack.yml` | The services: Traefik (HTTPS), PostgreSQL 17, SeaweedFS (documents), Keycloak (sign-in), backend, analytics, frontend, two backup jobs |
| `pmp.env` | Your settings (copy `pmp.env.example`). Not committed. |
| `create-secrets.sh` | Generates passwords and keys and registers them as Swarm secrets |
| `build.sh` / `deploy.sh` | Build and push the images; deploy or upgrade the stack |
| `keycloak/pmp-realm.json` | The sign-in realm, imported on Keycloak's first start |
| `postgres/10-roles.sh` | Creates the database roles on the database's first start |
| `backup/` | The nightly backup scripts |

## 1. First installation

**Servers.** One or more VPS nodes with Docker 27 or newer (the stack uses Traefik 3.6, which also
works with Docker 29), in one Swarm (`docker swarm init` on the
first, `docker swarm join` on the others). Label where the database and the documents live (can be
the same node, ideally with a separate data disk):

```sh
docker node update --label-add pmp.db=true <node>
docker node update --label-add pmp.storage=true <node>
```

**DNS and certificates.** Point the host name (for example `foa.kplc.co.ke`) at the manager. With
Let's Encrypt, ports 80 and 443 must be reachable from the internet; otherwise see section 7.

**Settings.**

```sh
cd deploy
cp pmp.env.example pmp.env
uuidgen            # paste as PMP_TENANT_ID; never change it afterwards
vi pmp.env         # host, registry, tenant, first administrator's email, ACME email, office CIDR
```

**Secrets.**

```sh
./create-secrets.sh
```

Copy `deploy/secrets/` to the offline backup now (an encrypted USB key in the safe, or the
corporate password vault). **`PMP_SEALING_MASTER_KEY` is the most important**: without it, sealed
bids already in the database can never be opened, and it is not in any database backup. Keep the
folder on the server until you have signed in to Keycloak below; then remove it with
`shred -u secrets/*`.

**Images and deployment.**

```sh
./build.sh 0.1.0        # builds and pushes backend, analytics, frontend
./deploy.sh
docker stack ps pmp     # wait until everything is Running
```

On first start: PostgreSQL creates the `pmp_app`, `pmp_analytics` and `keycloak` roles; Keycloak
imports the `pmp` realm; the backend runs the migrations, switches on row-level security, creates
the organisation with starter procurement settings, and makes `PMP_ADMIN_EMAIL` a system
administrator.

**Keycloak administrators.** Open `https://<host>/auth/admin` from the office network
(`PMP_ADMIN_ALLOW_CIDR`; the master realm is not reachable from elsewhere), sign in as `admin` with
the password in `secrets/KC_ADMIN_PASSWORD`, and in the **master** realm:

1. *Realm settings > Security defenses > Brute force detection*: turn on (lockout after 5 failures).
2. *Users*: create named administrator accounts for the people who will run Keycloak, with the
   *admin* role, then sign in as one of them and delete the temporary `admin` account.

Now remove the secrets folder from the server (`shred -u secrets/*`).

**The first PM Platform administrator.** In the **pmp** realm, *Users > Add user*: username, the
email from `PMP_ADMIN_EMAIL`, first and last name, tick *Email verified*, then *Credentials > Set
password* (temporary). They sign in at `https://<host>/` and see *Administration > Users and roles*.

**Roles.** The administrator adds people (section 2). Administrators cannot give themselves roles,
so the first administrator adds a second one, who grants the first any roles they need.

**Check before handing over.** The procurement thresholds, methods and committee rules are starter
placeholders, not legal values. The head of procurement (the only role that may change them)
reviews them on *Administration > Procurement settings* before the first requisition.

## 2. Adding people

Two steps, until Active Directory is connected (section 6):

1. **Keycloak** (pmp realm): *Users > Add user* with their work email, *Email verified* ticked,
   and a temporary password (or *Credentials > Reset password*, or send them a reset email once
   SMTP is set up under *Realm settings > Email*).
2. **PM Platform**: *Administration > Users and roles > Add person*, with the same email and their
   roles.

Their first sign-in links the two (the record shows *Linked*). Order does not matter. Someone who
signs in before step 2 sees "Your account is not set up yet".

**Leavers:** disable them in Keycloak (stops sign-in at once) and set them *Inactive* in PM
Platform (keeps their history and stops notifications). **Replaced Keycloak account** (deleted
and recreated): use *Unlink* on their PM Platform record so the new account links on next sign-in.

The realm locks an account for 1 minute (rising to 15) after 5 wrong passwords, needs passwords of
at least 12 characters, ends a session after 30 minutes idle or 10 hours, and has self-registration
turned off.

## 3. Upgrades

```sh
cd deploy
git pull                      # or unzip the new release
./build.sh 0.2.0
sed -i 's/^PMP_VERSION=.*/PMP_VERSION=0.2.0/' pmp.env
./deploy.sh
```

The backend starts the new version before stopping the old one and runs any new migrations at
start-up; take a manual backup first (section 4) when the release notes mention migrations. If a
file under `deploy/` changed (realm, scripts), bump `PMP_CONFIG_VERSION` in `pmp.env` too.
**Rollback:** set the previous `PMP_VERSION` and `./deploy.sh`. A release with migrations can
only be rolled back by restoring the backup taken before it.

The realm file is only imported on Keycloak's first start; later realm changes are made in the
Keycloak console.

## 4. Backups

Nightly at `PMP_BACKUP_HOUR` (Nairobi time), into `PMP_BACKUP_DIR`:

| What | Where | Kept |
|---|---|---|
| Platform and Keycloak databases (`pg_dump`), roles | `postgres/<date>/pmp.dump, keycloak.dump, roles.sql` on the db node; `COMPLETE` marks a good run | `PMP_BACKUP_KEEP_DAYS` days |
| Documents (current version of every file) | `documents/current/` on the storage node; `LAST_SUCCESS` has the time | always the latest |
| Secrets, sealing key | Your offline copy from installation | for good |

The bucket keeps older versions of changed files itself. These backups are on the same servers:
copy `PMP_BACKUP_DIR` off site every day (a corporate backup agent, or `rsync` to another site).

Check them: `docker service logs pmp_db-backup` and `pmp_docs-backup` should show a success every
night. Take one now (before an upgrade), on the db node:

```sh
docker exec -it $(docker ps -qf name=pmp_db-backup) sh -c \
  'export PGPASSWORD=$(cat /run/secrets/PMP_DB_MIGRATION_PASSWORD); d=/backups/manual-$(date +%F_%H%M); mkdir -p $d && pg_dump -Fc -f $d/pmp.dump pmp && pg_dump -Fc -f $d/keycloak.dump keycloak && ls -l $d'
```

**Rehearse a restore every quarter** on a spare server.

## 5. Restore

Database (on the db node; stops the platform while it runs):

```sh
docker service scale pmp_backend=0 pmp_analytics=0 pmp_keycloak=0
C=$(docker ps -qf name=pmp_postgres)
docker cp /srv/pmp-backups/postgres/<date>/pmp.dump $C:/tmp/pmp.dump
docker exec -it $C sh -c 'dropdb -U pmp pmp && createdb -U pmp pmp && pg_restore -U pmp -d pmp --no-owner --role=pmp /tmp/pmp.dump'
# the same for keycloak.dump into the keycloak database (createdb -U pmp -O keycloak keycloak, --role=keycloak)
docker exec -it $C psql -U pmp -d pmp -c 'GRANT CONNECT ON DATABASE pmp TO pmp_app, pmp_analytics'
docker service scale pmp_backend=1 pmp_analytics=1 pmp_keycloak=1
```

The backend re-applies the grants and row-level security at start-up. Documents:

```sh
docker run --rm --network pmp_data -v /srv/pmp-backups/documents/current:/restore:ro \
  -e AWS_ACCESS_KEY_ID=<key> -e AWS_SECRET_ACCESS_KEY=<secret> amazon/aws-cli:2.27.0 \
  --endpoint-url http://seaweedfs:8333 s3 sync /restore s3://pmp-documents
```

On a new server: install as in section 1, but before `./deploy.sh` recreate the **same** secrets
from the offline copy (`printf '%s' "$(cat PMP_SEALING_MASTER_KEY)" | docker secret create PMP_SEALING_MASTER_KEY -`
and so on for each file), use the same `PMP_TENANT_ID`, then restore as above.

## 6. Connecting Active Directory (later)

Kenya Power's directory can replace the Keycloak passwords without changing PM Platform:

1. Keycloak console, pmp realm: *User federation > Add LDAP provider*. Vendor *Active Directory*;
   connection URL `ldaps://<dc>:636` (import the AD certificate authority into Keycloak's
   truststore if needed); bind DN and password of a read-only service account; users DN (the OU
   with staff); username attribute `sAMAccountName` (or `userPrincipalName`); *Edit mode*
   READ_ONLY; *Trust email* **on** (PM Platform links people by verified email).
2. *Test connection*, *Test authentication*, then *Synchronize all users*.
3. Optional: an LDAP filter limiting sign-in to an AD group, for example
   `(memberOf=CN=PMP-Users,OU=Groups,DC=kplc,DC=co,DC=ke)`.
4. People already created by hand in Keycloak with the same username or email: delete them in
   Keycloak first, then use *Unlink* on their PM Platform record so the AD account links on next
   sign-in.

Roles stay in PM Platform (*Users and roles*). Mapping AD groups to roles is possible later but is
not needed for the pilot. Network: the Keycloak container must reach the domain controllers on
636; the `edge` network allows outbound traffic.

## 7. Certificates

Default: Let's Encrypt via Traefik (`PMP_ACME_EMAIL`), which only works when the host name is
reachable from the internet. Until a certificate is in place, browsers show "Not secure" because
Traefik serves its own self-signed one.

For an internal host with a corporate certificate:

1. Make a key and a certificate request on the manager, and send the `.csr` to ICT (keep the key
   on the server):
   ```sh
   cd /srv/pmp-tls
   openssl req -new -newkey rsa:2048 -nodes -keyout pmp.key -out pmp.csr \
     -subj "/CN=foa.kplc.co.ke" -addext "subjectAltName=DNS:foa.kplc.co.ke"
   chmod 600 pmp.key
   ```
2. Save the issued certificate followed by the intermediate certificate(s) as `pmp.crt` (full
   chain, PEM), and copy `traefik/certificates.yml.example` there as `certificates.yml`.
3. In `pmp.env` set `PMP_TLS_RESOLVER=` (empty), then `./deploy.sh`.
4. Check: `openssl s_client -connect foa.kplc.co.ke:443 -servername foa.kplc.co.ke </dev/null |
   openssl x509 -noout -subject -issuer -enddate`. Renew before the end date: replace `pmp.crt`
   (and the key if reissued); Traefik picks it up without a redeploy.

## 8. Security notes

- Never set `SPRING_PROFILES_ACTIVE=dev` or `PMP_ALLOW_TENANT_HEADER=true` on a server: they open
  the API without sign-in.
- The backend connects as `pmp_app`. Row-level security limits every query to the signed-in
  user's organisation even if application code forgot a filter: tables with a `tenant_id` directly,
  child tables through their parent row. The audit log cannot be changed or deleted by that role.
  Analytics connects as `pmp_analytics`, which can read project data but not the platform schema
  (people, roles, audit log).
- Administrators manage people but cannot give themselves roles; procurement thresholds can only
  be changed by the head of procurement. Both are in the audit log. This is a process control, not
  a hard wall: an administrator could add a second record under another email they control, and
  two administrators can grant each other roles. Keep administrators few, separate from approvers
  where possible, and review the `user.*` entries of the audit log monthly.
- Only Traefik publishes ports. PostgreSQL, SeaweedFS, the backend and analytics are on the
  internal `data` network.
- The Keycloak admin console is only reachable from `PMP_ADMIN_ALLOW_CIDR`.
- Rotate a password: create a new secret (for example `PMP_DB_APP_PASSWORD_2`), change the role's
  password in PostgreSQL, point the service at the new secret in `stack.yml`, deploy. The sealing
  key cannot be rotated without re-sealing open tenders; do not rotate it during a tender.

## 9. Troubleshooting

| Symptom | Look at |
|---|---|
| "Could not sign you in" / sign-in loops | `docker service logs pmp_keycloak`; `PMP_HOST` matches the address in the browser; the realm's redirect URIs (*Clients > pmp-web*) |
| "Your account is not set up yet" | The person is missing or inactive in *Users and roles*, the email differs, or *Email verified* is off in Keycloak |
| Every request 401 | Backend log: issuer or audience mismatch; `PMP_OIDC_ISSUER_URI` must be exactly `https://<host>/auth/realms/pmp` |
| Backend restarts at start-up | `docker service logs pmp_backend`: database password, migration error, malformed `PMP_TENANT_ID` |
| Uploads fail | `docker service logs pmp_storage-init` (bucket created?), `pmp_seaweedfs` |
