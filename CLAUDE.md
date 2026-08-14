# Church Management App — working notes

Multi-tenant parish administration platform. Spring Boot 4.1 · Java 17 · Thymeleaf ·
MySQL 8 · Gradle · Flyway.

Full documentation is in [docs/](docs/) — architecture, database schema, security,
payments, plus a 10-slide overview deck.

## How the user wants to work

**Ask before implementing. Every step.** The user approves each step individually and has
pushed back when work started without an explicit go-ahead. Answering a design question is
not approval to build — wait for "go".

Propose the step, state the decisions it needs, then stop. Small, verifiable steps.

## Environment

- MySQL 8.0.45 on localhost:3306, user `root` / password `admin`
- Schemas: `churchnew` (dev, has sample data) · `churchuat` · `churchprod` (both empty of
  sample data)
- Credentials live in `application-local.yml` at the **project root** — gitignored, and
  deliberately not under `src/main/resources`, because anything there is packaged into the
  jar and imported properties outrank environment variables
- MySQL client: `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`
- Git is installed on `H:`, not `C:` — `H:\Program Files\Git\git-bash.exe`
- No LibreOffice; PowerPoint 15.0 is available via COM for rendering decks

```bash
gradlew.bat build      # compile, migrate, run 57 tests
gradlew.bat bootRun    # http://localhost:8080
```

## Boot 4 gotchas already hit

Starter names changed from Boot 3. Tutorials will not match:

| Boot 3 | Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| `spring-boot-starter-test` | per-module test starters |

## Decisions already made — do not relitigate

- **Layer-based packages** (`controller`, `service`, `repository`), not feature-based
- **Credentials live on `member`** (`member_password`, `password_flag`), not a separate
  user table. Platform staff are in `saas_user`, which has no `church_id`
- **Two independent security chains**: `/login` reads `member`, `/saas/login` reads
  `saas_user`. Credentials valid on one are rejected by the other, by design
- **Login by email or mobile**; mobiles normalised to `+91…` form
- **RBAC**: `Operation` and `Resource` are Java enums; the role→permission mapping is data
  in `role_permission` (275 rows)
- **`password_flag`**: 0 = system-assigned, 1 = user chose it. Forced change while 0
- **Lockout**: 5 failures, no auto-unlock — an administrator clears `locked_at` by SQL
- **Forgot password** resets to a known shared value (`PleaseReset@123`). The user was
  warned this is an account-takeover path and accepted it; rate-limited to 3/hour
- **`role` is global**, not per-church
- **Table names keep no prefix** (`glb_`/`app_` was considered and rejected)
- **Payments**: three tables — `payment_due` (owed), `payment` (received),
  `payment_allocation` (which payment settled which month). Allocation is oldest-first
- **Receipt numbering**: per church, per calendar year, gapless, via `receipt_sequence`
  with a pessimistic lock
- **UI**: payment screens are mobile/tablet-first; everything else desktop-first but must
  degrade to usable. Receipts print to a 58mm POS thermal printer

## Traps discovered the hard way

- **Hibernate `@Filter` does not apply to primary-key loads.** `findById` bypasses it,
  which is why `TenantAwareRepositoryImpl` re-routes it through a criteria query
- **The tenant filter must be enabled inside the transaction.** `TransactionOrderConfig`
  moves the transaction advisor to high precedence so `TenantFilterAspect` runs within it.
  Get this wrong and queries are silently unfiltered — no error, no warning
- **`V1` was a baseline marker, not a script.** A fresh database could not be built at all
  until `V1__baseline_schema.sql` was written. Verified against an empty schema
- **Sample data lives in `db/seed`**, listed only by the dev profile. `db/migration` is
  schema and reference data, applied everywhere
- **Tests assert against live sample data in `churchnew`.** Using the running app to
  change a password once broke a test. Do not assert on mutable state like
  `password_flag` or `locked_at`
- **MySQL reports `TINYINT(1)` as `BIT`** — flag columns map to `boolean`, not `int`
- **pptxgenjs**: negative width/height on a shape produces a file PowerPoint refuses to
  open, and the schema validator does not catch it

## Sample accounts

Password `Welcome123$` for all. Parish → `/login`, platform → `/saas/login`.

| Role | Identifier |
|---|---|
| SaaSSAdmin | `superadmin@churchapp.local` / `9000000001` |
| AppSA (St. Mary's) | `antony.raj@stmarys-chennai.org` / `9840100001` |
| AppAdmin (St. Mary's) | `mary.arulraj@example.com` |
| AppUser (St. Mary's) | `stephen.d@example.com` |

Three churches: St. Mary's Chennai (6 members), St. Joseph's Madurai (3), Our Lady of
Lourdes Trichy (2). Anbiyam names are Tamil — utf8mb4 throughout.

## What is built

Login (both chains) · dynamic RBAC · password lifecycle · lockout · audit trail ·
full payment data model · **tenant isolation on reads** · dev/uat/prod profiles ·
57 tests.

## What is NOT built — the next work

1. **Bootstrap admin — blocking.** `churchuat` and `churchprod` have zero accounts and
   every page is behind a login, so a fresh deployment cannot be signed into at all
2. **Nothing checks permissions.** `@PreAuthorize` appears in one comment. The 275
   permission rows are loaded as authorities at sign-in and never consulted
3. **`created_user` / `updated_user` are never populated** — no `AuditorAware`
4. **No page layout fragment** — every module would duplicate head and topbar
5. **Write-side tenant stamping** — reads are protected, writes are not. A form posting a
   hidden `churchId` would currently be trusted
6. **No CRUD screens at all** — dashboards are placeholders
7. **Payment service layer** — the model is complete and tested, but nothing records a
   receipt, allocates, or voids
8. **No admin unlock screen** — locked accounts need SQL
9. **Receipt printing** and the Indian amount-in-words utility (lakh/crore, not millions)
10. **No CI**

Suggested order: bootstrap admin → JPA auditing → layout fragment → then the first module,
built with `@PreAuthorize`, audit calls and tenant stamping so it becomes the template.

## Git

Remote is `https://github.com/santaigh/church-app.git`. If the GitHub repo is renamed to
`church-mgmt-app`, update it:

```bash
git remote set-url origin https://github.com/santaigh/church-mgmt-app.git
```

Never commit `application-local.yml`. Schema changes go in a migration, never straight
into the database — V16 exists solely to repair a constraint applied by hand.
