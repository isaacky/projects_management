# PM Platform

Enterprise project management from procurement to implementation. First design
partner: Kenya Power.

This repository holds three applications and the local infrastructure they need.

| Folder | What it is | Stack |
| --- | --- | --- |
| `backend/` | Core API, modular monolith | Java 25, Spring Boot 4.1.1, Maven, PostgreSQL 17, Flyway |
| `frontend/` | Web app | React (plain JavaScript), MUI, MUI X Data Grid, TanStack Query, Vite |
| `analytics/` | Portfolio analytics API | Python 3.12+, FastAPI, Polars, SQLAlchemy |
| `docker-compose.yml` | Local stack | PostgreSQL 17, SeaweedFS (S3 storage), Redis, Keycloak (optional), plus the three apps |
| `deploy/` | Servers | Docker Swarm stack, Keycloak realm, backups, [runbook](deploy/RUNBOOK.md) |

## What works today

The first slice is the **project spine and lifecycle gates**:

- Create projects (code, name, sponsor, budget in KES minor units).
- Move a project through the six phases (Initiation → Procurement → Planning →
  Execution → Closeout → Implementation). Each move records a gate decision (G0 to G4).
- Every change is written to a **hash-chained, append-only audit log**. The database
  itself refuses updates or deletes on that table.
- Multi-tenant from the first table: every row carries `tenant_id` and every query is scoped.
- Web app: dashboard, projects grid, project page with lifecycle stepper, gate
  approval dialog and audit history.
- Analytics: active projects and budgets by phase (`/portfolio/summary`).

**Procurement settings** (per tenant, stored in the `procurement` schema, editable under
Administration → Procurement settings):

| Table | Holds |
| --- | --- |
| `procurement.methods` | Methods in use (open tender, restricted tender, RFQ, direct, framework, or any other), the value range each may be used for, minimum bid period, advert and justification requirements |
| `procurement.approval_thresholds` | Amount bands per decision (requisition, award, contract variation) and the approver for each step. Several steps on one band form an approval chain. A band for one method overrides the general bands. Bands include their lower limit and exclude their upper limit. |
| `procurement.evaluation_committee_rules` | Committee size and quorum, whether technical and financial bids are opened separately (two-envelope) with a pass mark, conflict-of-interest declarations, who signs the report, who reviews it and who approves the award. A rule with no method is the default. |

The `/check` endpoint and the "Check a purchase" tab apply these rules to a purchase: which
methods are allowed, the approval chain and the committee rule. The procurement workflow
(next) will call the same rules. The dev sample values are placeholders, not legal thresholds.

**Annual procurement plan** (Lifecycle → Procurement plan):

- **Prepare:** procurement opens the plan for a financial year (July to June by default) and adds
  the departments, each with a head and an optional budget ceiling. A new plan can start with last
  year's departments.
- **Department lines:** each head enters what the department will buy. For each line they give the
  category, quantity, estimated cost, procurement method, the quarter procurement should start,
  the source of funds, the budget code, any AGPO reservation and, optionally, a project.
  - The method must be allowed for the estimate under Procurement settings.
  - Lines can also be imported from a CSV saved from Excel. A template is in the import dialog,
    and nothing is imported if any row has a problem.
  - The head submits the department's lines. Submission is refused above the ceiling.
- **Consolidate and approve:** procurement can return a department's lines or adjust them. Once
  every department has submitted, the head of procurement submits the plan to the accounting
  officer, who approves it or returns it with a note.
  - The plan warns when the share reserved for AGPO groups is below the 30% target (the target can
    be changed).
  - The consolidated plan exports to CSV.
- **Requisitions against the plan:** once a plan is approved, a requisition submitted in that
  year must do one of two things:
  - Name a plan line and fit within what is left on it (the approved estimate less other
    submitted or approved requisitions).
  - Or say why the purchase is not in the plan. An unplanned requisition gets an extra accounting
    officer step, and appears in the plan's unplanned list.
  - Before a plan is approved for the year, requisitions work as before.
- **Tracking:** each line shows what has been requisitioned, whether a tender is running, and the
  awarded amount. Lines whose quarter has ended with no requisition are flagged as behind. The
  summary breaks the plan down by quarter, category and method.
- **Revision:** the head of procurement reopens an approved plan with a reason. Lines can be added,
  changed or dropped, and the accounting officer approves the revision. Until then, requisitions
  use the approved amounts. A dropped line stops taking requisitions at once, and a line's
  estimate cannot go below what requisitions already draw on it. Each approval is kept.

**Trying it locally:** the sample 2026/27 plan is being prepared. Network Operations has submitted
its lines, while ICT and Corporate Services have not.

| Step | Viewing as |
| --- | --- |
| Enter or import ICT's lines and submit them | *Sample Requester* (head of ICT) |
| Submit Corporate Services (or add lines first) | *PMO Lead (sample)* |
| Return or adjust a department's lines | *Sample Procurement Officer* |
| Submit the plan | *Sample Head of Procurement* |
| Approve or return the plan | *Sample Accounting Officer* |
| Raise a requisition on a plan line, or as unplanned | *Sample Requester* |

**Requisitions** (Lifecycle → Requisitions, or "Raise requisition" on a project):

- A requester raises a requisition on an active project with line items; totals are computed per
  line to the cent. It is saved as a draft (numbered REQ-2026-0001, ...) until submitted.
- On submit, the approval chain is copied from the REQUISITION approval thresholds for that amount,
  so later settings changes never affect a requisition already in approval.
- Approvers act in step order: approve, return for changes (the requester edits and resubmits;
  a new round starts) or reject (final). Reject and return need a reason. Nobody can approve their
  own requisition, and each step needs the right role.
- The requester can withdraw a requisition until it is approved.
- Everyone who must act gets an **in-app notification** (bell icon, refreshed every 30 seconds).
  Email can later be sent from the same notifications table.

**Trying the approval chain locally:** the top bar has a **Viewing as** picker (development only).
Raise and submit a requisition as *Sample Requester*, then switch to *Sample Head of Department*
(and, above KES 5m with the sample settings, *Sample Accounting Officer*) to approve it.

**Suppliers** (Lifecycle → Suppliers): the supplier register. Add suppliers one by one or
**import a CSV** of the existing list. The import shows a preview first (errors, warnings, new vs
updated rows) and matches existing suppliers by KRA PIN. Download the template from the import
dialog. Excel files must be saved as CSV first. Debarred or suspended suppliers cannot bid.

**Tenders and sealed bids** (Lifecycle → Tenders, or "Create tender" on an approved requisition):

- A procurement officer creates a tender from an approved requisition. The method must be allowed
  for that amount by the settings; the method and committee rule are copied onto the tender.
- Add tender documents, invite suppliers (required for restricted methods), set the closing time
  (at least the method's minimum bid period after publishing), then publish. Closing can be
  extended with a reason, and documents added later are listed as addenda.
- **Staff upload bids** received before closing: one file set (single envelope) or technical and
  financial sets (two-envelope). Each bid gets a receipt number and a receipt hash (SHA-256 over
  the tender, supplier, time and every file's hash) that can be given to the supplier.
- Files are **encrypted as they are stored** (AES-256-GCM, a separate key per tender). Nobody can
  see file names or download files until the envelope is opened. The server refuses bids after
  closing.
- **Opening** happens only after closing and needs the committee quorum of attendees from the
  committee rule. With two envelopes the technical envelope opens first, then the financial one.
  Read-out prices are recorded at the financial opening, and scans of paper bids can be added
  after opening (marked as such).
- Every step, including each download of a bid file, is in the tender history (audit log).

**Trying it locally:** switch to *Sample Procurement Officer*, create a tender on an approved
requisition, upload a document, publish with a closing time a few minutes past the minimum bid
period (or lower the method's minimum bid period in Procurement settings while testing), record
bids, wait for closing, then open with four attendees.

**Sealing key.** `PMP_SEALING_MASTER_KEY` (32 random bytes, base64: `openssl rand -base64 32`)
wraps every tender's key. It is required outside the dev profile, and the backend will not start
without it. **Back it up separately from the database and storage**: if it is lost, sealed bids
can never be opened. Today the key sits in the server's environment, so someone who holds the key,
the database and the storage together could read bids early; moving it to Vault or a split key is
planned.

**AGPO reserved tenders.** A tender can be reserved for youth, women, persons with
disabilities, or any AGPO group. Only suppliers with a valid certificate for that group can then
be invited or have a bid recorded.

**Bid evaluation and award** (the Evaluation tab on an opened tender):

1. **Set-up** (procurement staff): pick a criteria template, adjust the criteria to match the tender
   document, and propose the committee (chair, members, optional non-voting secretary). Committee
   size, quorum, technical pass mark and report signatories come from the committee rule.
2. **Appointment** (accounting officer): appoints the committee. They cannot sit on it.
3. **Declarations:** each member declares any conflict of interest with the bidders. A member with a
   conflict takes no further part. Until they declare, members cannot see bid documents or mark bids.
4. **Preliminary** (pass/fail) and **technical** (scored) stages: each member marks every bid on
   their own. Marks stay private until the chair closes the stage, which needs at least a quorum of
   members to have finished.
   - A preliminary criterion is met when most members say so; on a tie, the chair's mark decides.
   - Technical scores are the members' averages, compared with the pass mark.
   - Bids that fail are out.
5. **Financial:** in a two-envelope tender the financial envelopes are opened only after the
   technical stage closes, and only for the bids that passed. The others stay sealed for good.
   The chair records each evaluated price; a correction note is needed when it differs from the
   price read out. Bids are ranked by price.
6. **Report:** the chair recommends the lowest evaluated bid (or another, with a reason, or no award).
   The signatories sign, and can add a dissent note.
7. **Opinion and approval:** the steps follow the AWARD approval chain in the settings. The head of
   procurement gives the professional opinion first, then the accounting officer decides: approve,
   return to the committee (everyone signs again), or terminate. Committee members cannot approve.
8. **Award:** the tender is AWARDED, and the standstill period (per method, default 14 days) is shown
   before the contract can be signed. Award and regret letters, with the reasons, are generated for
   printing.

**Trying it locally:** open the technical envelopes, then follow the role each step needs:

| Step | Viewing as |
| --- | --- |
| Set up the evaluation; propose the 5 *Sample Evaluator* users with one as chair | *Sample Procurement Officer* |
| Appoint the committee | *Sample Accounting Officer* |
| Declare, mark the bids, close stages | Each *Sample Evaluator* (the chair closes stages) |
| Open the financial envelopes | *Sample Procurement Officer* |
| Professional opinion | *Sample Head of Procurement* |
| Award decision | *Sample Accounting Officer* |

Evaluation criteria templates are under Procurement settings > Evaluation criteria. The dev samples
are typical Kenyan tender criteria; replace them with Kenya Power's standard ones.

**Contracts** (Lifecycle → Contracts, or "Prepare contract" on an awarded tender):

- **Draft** (procurement): the contract is made from the award (supplier, evaluated price,
  project). Set the dates, retention, defects liability period, required performance security,
  the contract manager and the payment schedule (milestones adding up to the contract value).
  A request for review to the Review Board can be recorded: while it is pending the contract
  cannot be signed, and if it is upheld the draft is cancelled.
- **Signing** is refused until the standstill period has ended, the performance security is
  held, the schedule matches the value and the signed copy is uploaded. The page lists what is
  still missing.
- **Payments:**
  - The contract manager accepts each milestone after inspection.
  - Supplier invoices get a payment certificate number, with retention withheld at the
    contract's rate.
  - The contract manager certifies the invoice, then a finance officer approves it (never one
    they certified) and records the payment with its reference.
  - Invoices cannot exceed the contract value or the milestone's value.
- **Variations** of value or time follow the CONTRACT_VARIATION approval chain in the settings.
  Increases are capped at 25% of the original price in total (PPADA s.139), and the person who
  requested a variation cannot approve it.
- **Securities:** expiry is tracked, with a daily 07:00 reminder 30 days before a security
  expires. A security can be extended, released, or called (calling needs the accounting
  officer).
- **Completion:** the contract manager completes the contract once every milestone is accepted,
  and rates the supplier (quality, timeliness, compliance). The rating shows under "Past
  performance" in the supplier register. Retention is released after the defects liability
  period. The accounting officer can terminate an active contract.
- **Project page:** shows committed, certified and paid amounts against the budget.

**Trying it locally:** after an award, prepare the contract as *Sample Procurement Officer*.
Assign *Sample Contract Manager*, add the milestones, security and signed copy, and wait out
the standstill period (or award the tender with a 0-day standstill set on the method). Then:

| Step | Viewing as |
| --- | --- |
| Accept milestones, certify invoices | *Sample Contract Manager* |
| Approve and pay | *Sample Finance Officer* |
| Approve variations | *Sample Accounting Officer* |

Payments are tracked here, not in the ERP; a link to Kenya Power's ERP can come later.

**Planning** (the Schedule tab on a project):

- **Plan:** the project manager builds the work breakdown in a grid (indent/outdent for summary
  rows, durations in calendar days, predecessors such as `3FS+2` or `2SS`, a "not before" date,
  optional weights and an owner), or imports an MS Project XML file (*File → Save As → XML*;
  up to 10 MB and 3,000 tasks). The critical path is worked out on every save and shown in red on
  the Gantt chart.
- **Baseline:** freezes the planned dates. Changing a baseline needs the PMO lead and a reason;
  rows added later are measured against the current plan until the next baseline.
- **Progress:** the project manager, the PMO or the activity's owner records actual start,
  percent complete and actual finish on the lowest-level rows. The forecast moves with it: work
  not started cannot start before today, and work in progress finishes after its remaining days.
  Each day with progress adds a point to the S-curve (planned against earned).
- **Figures:** planned % (against the baseline), earned % (weighted by duration unless a weight is
  set), SPI = earned ÷ planned, and slip in days between the baseline and forecast finish.

Scheduling uses calendar days for the pilot; working calendars and holidays come later.

**Execution** (the Risks, Issues and Status reports tabs, and Portfolio health on the dashboard):

- **Risks:** anyone on the project can raise one. Probability × impact on a 5×5 matrix gives the
  level (1–4 low, 5–9 medium, 10–16 high, 20–25 critical), and a heat map shows open risks. The
  owner is notified, and overdue actions are flagged.
- **Issues:** priority, owner, due date and resolution, optionally linked to the risk that
  became the issue.
- **Status reports:** the project manager writes one per period. Schedule, cost and risk
  ratings are suggested from the figures (SPI and slip; CPI and commitments against budget;
  critical and high risks) and can be overridden. Submitting freezes the figures, and a PMO lead
  other than the one who submitted it reviews the report.
- **Portfolio health:** every project with its latest ratings, SPI, CPI and open high risks.

CPI here is earned value (earned % × budget) over the contract amounts certified so far. That
understates actual cost until payments are linked from the ERP, so read it as a guide.

**Trying it locally:** open a project and go to the Schedule tab.

| Step | Viewing as |
| --- | --- |
| Build or import the plan, set the first baseline, record progress, write and submit the status report | *Sample Requester (Project Manager)* |
| Re-baseline, review status reports | *PMO Lead (sample)* |
| Record progress on an activity they own | The activity's owner |
| Raise risks and issues | Anyone, e.g. *Sample Head of Department* |

## Prerequisites

- JDK 25 (Temurin recommended) and Maven 3.9+
- Node.js 22+
- Python 3.12+
- Docker with Compose v2

## Quick start

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env.local

# 1. Infrastructure: PostgreSQL 17, SeaweedFS (S3), Redis
docker compose up -d
docker compose logs storage-init   # shows whether the documents bucket was created

# 2. Backend (dev profile: no login, sample Kenya Power pilot data)
cd backend
mvn spring-boot:run            # uses the dev profile by default
# From an IDE, set the active profile to "dev"; without it every API call returns 401.
# -> http://localhost:8080/actuator/health

# 3. Analytics (new terminal)
cd analytics
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
PMP_ALLOW_TENANT_HEADER=true uvicorn app.main:app --reload --port 8000

# 4. Frontend (new terminal)
cd frontend
npm install
npm run dev
# -> http://localhost:5173
```

Or run everything in containers:

```bash
docker compose --profile app up -d --build
# Web app on http://localhost:8081, SeaweedFS file browser on http://localhost:8888
```

**Signing in as on a server** (Keycloak, row-level security), still with the sample data:

```bash
docker compose --profile sso up -d                    # Keycloak on http://localhost:8180 (admin / admin)
cd backend && SPRING_PROFILES_ACTIVE=dev,sso mvn spring-boot:run
cd frontend && npm run dev:sso                        # http://localhost:5173 -> Keycloak sign-in page
cd analytics && pip install -e ".[dev]" \
  && PMP_OIDC_ISSUER_URI=http://localhost:8180/realms/pmp uvicorn app.main:app --reload --port 8000
```

Sign in with any sample user's email (for example `pmo.lead@example.com`, who is also the system
administrator) and the password `pmp-dev-password`. The backend then connects as the restricted
database role `pmp_app`, so row-level security applies exactly as on a server.

**Closeout and handover** (the Closeout tab on a project, once it is in execution):

- **Completion checklist:** starting closeout copies the organisation's standard checklist onto
  the project. The PMO maintains that list under Administration → Completion checklist; until then
  a built-in list is used (completion certificates, commissioning tests, as-built drawings, O&M
  manuals, spares, training, asset register, wayleave records, final accounts, site
  reinstatement, safety and environment, records).
  - The project team, or whoever holds an item's responsible role, marks each item done or not
    applicable.
  - Some items need evidence (an uploaded file) to be marked done, and not applicable needs a
    reason.
- **Defects:** anyone can report a defect, with a severity, location and optionally the contract it
  falls under. The contract manager is notified, and the PMO too when it is critical.
  - The project team or that contract's manager records it as put right.
  - The project team or the receiving officer then verifies it, but never the same person who
    recorded the fix.
  - The PMO can waive a defect with a reason.
  - Only the project team can change a defect's severity or contract.
  - While a contract has defects outstanding, its retention cannot be released.
- **Handover to implementation:** the project manager names the receiving unit and officer, the
  handover date, and the assets handed over (with tags, locations and values), and attaches the
  handover documents.
  - Submission needs every checklist item finished, every contract completed or ended, the
    schedule 100% complete (if there is one) and no critical defect outstanding.
  - Only the named receiving officer accepts or rejects the handover, and never the person who
    submitted it. Acceptance re-checks those conditions.
  - Acceptance freezes the final figures: budget, contract value, certified, paid, baseline
    against actual finish, and the end of the defects liability period.
  - Gate G4 (formal closure, into implementation) stays closed until the handover is accepted.
- **Closing the project:** after G4, the PMO lead closes the project. Every defect must be dealt
  with, the defects liability period over, retention released on completed contracts, and no
  contract still draft or active.
- **Lessons learned:** anyone can record a lesson on a project at any time, as something that went
  well or something to improve, with a category and a recommendation.
  - The PMO reviews each lesson once, as adopted (with a note on what changes) or noted.
  - The Lessons learned page (under Portfolio) searches lessons across all projects.

**Trying it locally:** use project MTR-2026-002 (in execution).

| Step | Viewing as |
| --- | --- |
| Start closeout, complete the checklist, prepare and submit the handover | *Sample Requester (Project Manager)* |
| Report and verify defects, accept or reject the handover | *Sample Operations Officer* (name them as receiving officer) |
| Pass gate G4 on the Overview tab, waive defects, close the project, review lessons | *PMO Lead (sample)* |
| Change the standard completion checklist | *PMO Lead (sample)* |

**Benefits realisation** (the Benefits tab on a project, the Benefits page under Portfolio and a
card on the dashboard):

- **Benefits register:** the project manager or the PMO records what the project should achieve.
  Each benefit has:
  - a category and the business-case objective it serves;
  - an owner, who can be anyone in the organisation;
  - a measure (KPI) with its unit, a baseline with a date, and a target with a date;
  - how often it is measured, and optionally its yearly value in money once the target is reached.
- **Measurements:** the benefit owner (or the project team) records readings. A reading can't be in
  the future or before the baseline date, and there is one per day.
  - Progress is how far the latest reading has come from the baseline towards the target.
  - It is compared with where it should be by now, on a straight line from the baseline date to
    the target date: on track (at least 90% of that), at risk (60%) or off track. Target reached
    counts as achieved.
  - Each benefit shows a small chart of its readings, when the next one is due and, when it has a
    money value, how much has been realised.
- **Post-implementation reviews:** plan reviews by date, or plan the usual 6- and 12-month reviews
  from the accepted handover. The review lead writes up:
  - when the review was held;
  - whether the business-case objectives were met;
  - a summary and recommendations;
  - a rating and comment for each benefit.

  Completing the review freezes each benefit's figures as they stood.
- **Closing a benefit:** the project team closes a benefit as achieved, not achieved or cancelled,
  with a reason. The PMO can reopen it. Benefits outlive the project, so they can still be measured
  after it closes.
- **Reminders:** every morning, owners are reminded of readings that have fallen due (once per due
  date), and review leads a week before their review.

**Trying it locally:** the sample metering (MTR-2026-002) and rural network (NET-2026-001)
projects have benefits, owned by *Sample Operations Officer*, with some readings.

| Step | Viewing as |
| --- | --- |
| Add or change benefits, plan reviews, close benefits | *Sample Requester (Project Manager)* |
| Record readings, lead and complete a review | *Sample Operations Officer* |
| Reopen a closed benefit, see the portfolio | *PMO Lead (sample)* |

**Reports** (Lifecycle → Reports): a catalogue of reports, each with filters, shown on screen
and downloadable as an Excel workbook or CSV, or printed (or saved as PDF from the print dialog).
Clicking a row opens the project, contract or plan behind it.

| Report | Shows |
| --- | --- |
| Portfolio status | Each project's planned and earned progress, SPI and CPI, forecast finish and slip, budget, committed and certified, open risks and issues, and its latest status rating |
| Risk register | Risks across all projects, highest score first, with response, owner and due date |
| Procurement plan against actual | Each plan line's estimate and approved amount against what is requisitioned and awarded, how far it has got, lines behind schedule, unplanned requisitions and the AGPO share |
| Contract register | Contracts with original and current value, variation %, certified, paid, retention held, end date and defects period |
| Invoices and payments | Invoices and payment certificates in a period, with gross, retention, net, status and payment reference |
| Supplier performance | Each supplier's contracts, completions, average rating and total value |
| Benefits realisation | Each benefit against its target, its rating, the next reading due and the money realised |

- Excel files are written by the backend with no extra library. They have a title, the filters
  used, a frozen and filterable header, real dates and numbers (money in shillings with two
  decimals) and a bold totals row.
- Each Excel export is recorded in the audit trail.
- The reports read through the same services as the screens, so their figures match.
- Amounts in another currency are listed with their currency but left out of the KES totals.
  Rejected invoices are left out of the payment totals.

**Users and roles** (Administration → Users and roles, system administrators only):

- People sign in through **Keycloak** (later connected to Kenya Power's Active Directory). Their
  roles live in the platform: an administrator adds each person with the email they sign in with
  and picks their roles. The first sign-in with that (verified) email links the Keycloak account to
  the record; after that the account is matched by its Keycloak id, not the email.
- Deactivate leavers (their history stays); *Unlink* a record whose Keycloak account was recreated.
- Administrators cannot give themselves roles, deactivate themselves or drop their own
  administrator role (another administrator has to).
- Someone who signs in without having been added sees "Your account is not set up yet".
- Procurement settings can now only be changed by the head of procurement.

**Pilot deployment** (`deploy/`): a Docker Swarm stack with Traefik (HTTPS), PostgreSQL,
SeaweedFS, Keycloak (served under `/auth`), the three apps, and nightly database and document
backups. `deploy/RUNBOOK.md` covers first installation, secrets (including backing up the sealing
key), adding people, upgrades, restores, connecting Active Directory and certificates. On first
start the backend creates the organisation with starter procurement settings and makes
`PMP_ADMIN_EMAIL` the first system administrator.

## Tests

```bash
cd backend   && mvn verify          # unit tests + Testcontainers test (needs Docker)
cd frontend  && npm run lint && npm test
cd analytics && ruff check . && pytest
```

The same checks run in GitHub Actions (`.github/workflows/ci.yml`).

## Trying the API

The `dev` profile reads the tenant from the `X-Tenant-ID` header. The sample tenant is
`01920000-0000-7000-8000-000000000001`.

```bash
T=01920000-0000-7000-8000-000000000001

curl -s localhost:8080/api/v1/projects -H "X-Tenant-ID: $T"

curl -s localhost:8080/api/v1/projects -H "X-Tenant-ID: $T" \
  -H 'Content-Type: application/json' \
  -d '{"code":"NET-2026-010","name":"Feeder upgrade","budgetMinor":25000000000}'

curl -s localhost:8080/api/v1/projects/<id>/advance -H "X-Tenant-ID: $T" \
  -H 'Content-Type: application/json' \
  -d '{"decisionNote":"Approved by the investment committee"}'

curl -s localhost:8080/api/v1/projects/<id>/history -H "X-Tenant-ID: $T"

curl -s localhost:8000/portfolio/summary -H "X-Tenant-ID: $T"

# Procurement settings
curl -s localhost:8080/api/v1/procurement/settings -H "X-Tenant-ID: $T"
curl -s "localhost:8080/api/v1/procurement/settings/check?stage=AWARD&method=RFQ&amountMinor=150000000" \
  -H "X-Tenant-ID: $T"
```

| Settings endpoint | Does |
| --- | --- |
| `GET /api/v1/procurement/settings` | All three tables |
| `PUT /api/v1/procurement/settings/methods/{CODE}` | Create or update a method |
| `POST /api/v1/procurement/settings/approval-thresholds` | Add an approval step (overlapping bands for the same step are refused) |
| `PUT` / `DELETE /api/v1/procurement/settings/approval-thresholds/{id}` | Change or remove a step |
| `PUT /api/v1/procurement/settings/committee-rules/{CODE or DEFAULT}` | Create or update a committee rule |
| `DELETE /api/v1/procurement/settings/committee-rules/{CODE}` | Remove a method rule (the default cannot be removed) |
| `GET /api/v1/procurement/settings/check?stage=&method=&amountMinor=` | Apply the rules to one purchase |

Errors use RFC 9457 problem details (`application/problem+json`).

## Conventions

- **Bounded contexts.** One Java package and one PostgreSQL schema per context
  (`platform`, `portfolio`, then `procurement`, `contracts`, ...). Contexts never
  read each other's tables.
- **Money** is a `long` of minor units plus an ISO 4217 code. Never floating point.
- **IDs** are UUIDv7 (time-ordered).
- **Time** is stored in UTC (`timestamptz`) and shown in the user's time zone.
- **Migrations** are Flyway files in `backend/src/main/resources/db/migration`, never edited
  once merged. Dev-only sample data lives in `db/devdata` and runs only with the `dev` profile.
- **Audit.** Any state change that matters calls `AuditService.record(...)` inside the same
  transaction.

## Security status

| Area | Status |
| --- | --- |
| Sign-in | Keycloak (OIDC, authorization code with PKCE). The web app keeps tokens in memory, refreshes them while someone is active, and signs out of Keycloak too. Realm: 12-character passwords, lockout after 5 failures, 30-minute idle timeout, no self-registration. |
| Backend outside `dev` | OAuth2 resource server: checks the token's signature, issuer (`PMP_OIDC_ISSUER_URI`) and audience (`pmp-api`). The tenant comes from the token's `tenant_id` claim (set per realm in Keycloak). |
| Backend `dev` profile | Open API, tenant from the header. Never enable on a server. `dev,sso` signs in through the local Keycloak instead. |
| Analytics | Validates the same tokens (PyJWT, keys from Keycloak) and takes the tenant from them. The header only works with `PMP_ALLOW_TENANT_HEADER=true` (development). |
| Row-level security | Every table with a `tenant_id` has a policy limiting rows to the connection's tenant, and every child table one requiring its parent row to be visible (`app.tenant_id`, set by the backend for each pooled connection and by analytics per request). Servers connect as `pmp_app` / `pmp_analytics`, which cannot bypass it; migrations run as the owner. `pmp_app` cannot change or delete the audit log. Applied by `afterMigrate__row_level_security.sql` after every migration, so new tables are covered automatically. |
| Users and roles | Roles live in `platform.user_roles` and are managed on the Users and roles page. Accounts link on first sign-in by verified email. |
| Secrets | Docker Swarm secrets, read as files (`spring.config.import: configtree`). |
| Settings permissions | Procurement settings: head of procurement. Completion checklist: PMO lead. Users and roles: system administrators, who cannot give themselves roles. |

## Notes on the scaffold

- It was written in a sandbox that could not reach Maven Central, npm or PyPI.
  The database migrations were run against PostgreSQL, the JSX was bundle-checked, and the
  Python was syntax-checked. The first `mvn verify`, `npm install` and `pip install`
  happen on your machine or in CI. If anything fails there, it will most likely be a
  version pin; the backend follows the starter names and Testcontainers 2 packages
  used by Spring Boot 4.1.1.
- No `package-lock.json` is committed yet. Run `npm install` once and commit the lock file.
- Object storage is **SeaweedFS** (S3 API on port 8333). MinIO's community edition stopped
  publishing images in October 2025 and is no longer maintained. S3 access keys are set in
  `deploy/seaweedfs/s3.json`; if you change `PMP_S3_ACCESS_KEY` / `PMP_S3_SECRET_KEY` in `.env`,
  change them there too. The `storage-init` container creates the documents bucket with
  versioning, and with object lock where the SeaweedFS version supports it (its log says which).

## Next steps

1. Run the pilot installation with Kenya Power's infrastructure team (deploy/RUNBOOK.md); connect
   Active Directory to Keycloak when the directory team is ready (runbook section 6).
2. Working calendars for schedules; an ERP link for payments, budget checks and the asset register
   once Kenya Power's ERP is known; scheduled report emails.
3. Before running more than one backend replica: a lock for the daily reminder jobs.
