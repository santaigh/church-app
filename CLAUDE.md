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
- **App shell is a left sidebar**, collapsing to a drawer on phones and tablet portrait.
  Chosen over dashboard tiles and a top menu bar because the module list keeps growing
- **Tables become stacked cards below 640px** — everywhere, not only on payments, so
  every module copies one rule rather than two that drift apart
- **Parish priest is an appointment history**: one row per church posting, `to_date IS
  NULL` means currently serving. `active_flag` is deliberately NOT used on that table —
  two columns answering "who is current" can disagree, with nothing to catch it
- **Three clergy roles share that table**: `PARISH_PRIEST` · `ASSISTANT_PRIEST` ·
  `BROTHER` (a final-year seminarian, not ordained). Exactly one Parish Priest serves at
  a time; assistants and brothers are optional and may be several. The table keeps the
  name `parish_priest` — renaming was considered and declined
- **Appointing a priest auto-closes the open row** at the new start date, so a church can
  never show two current priests. Enforced in the service: MySQL has no partial unique
  index. Auto-closing applies to `PARISH_PRIEST` only — assistants must not close each
  other, since a parish may have several at once
- **A parish must have a Parish Priest, but it is not enforced strictly.** Real parishes
  have gaps between postings, so ending a posting without naming a successor is allowed;
  the parish shows a "no parish priest recorded" warning until one is appointed
- **Only SaaSSAdmin and SaaSAdmin may appoint any clergy** — parish priest, assistant or
  brother. Parish staff see who serves them but cannot change it: an appointment is a
  diocese-level act
- **`member.catagory` means the member's position in the family**, not their job:
  `HEAD · SPOUSE · CHILD · FATHER · MOTHER`, as a `FamilyRole` Java enum (not a table,
  not yaml — the values are queried in code, so they should be compile-checked). `HEAD`
  covers both Kudumba Thalaivan and Thalaivi; `gender` distinguishes them, and likewise
  turns `CHILD` into son or daughter, so no gendered values are needed. **One HEAD per
  family**, enforced in the service. Occupation is separate and already exists, on
  `member_ext.occupation`
- **English-only UI now, Tamil later.** From the Anbiyam module onward screen text uses
  `MessageSource` keys rather than literals, so Tamil is one `messages_ta.properties`
  away with no code change. Interface text is the only thing this touches — member and
  anbiyam names are data, already Tamil, already utf8mb4

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
- **`member.catagory` is spelled wrong and holds the wrong data.** The seed put parish
  duties and occupations in it — `PRIEST`, `ANIMATOR`, `SECRETARY`, `STUDENT`,
  `HOMEMAKER`, `TEACHER` — none of which is a family position. The member module has to
  remap it and rename the column to `family_role`. **Open question:** parish duties
  (priest, secretary, animator) then have nowhere to live; the user is checking whether
  they are needed at all
- **Tamil on the 58mm thermal printer** may not be possible as text — those printers
  carry a built-in character set. Receipts may have to be rendered as an image. Worth
  settling before Tamil goes live, not after

## Sample accounts

Password `Welcome123$` **except** `superadmin@churchapp.local` and
`antony.raj@stmarys-chennai.org`, whose passwords were changed through the running
application and are known only to the user. Those two now have `password_flag = 1`, so
they skip the forced-change screen; every other account still lands on it at first
sign-in. Never assert on `password_flag` in a test — it is mutable state.

Parish → `/login`, platform → `/saas/login`.

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
**shared page layout with the per-church logo header** · 61 tests.

The layout lives in `templates/fragments/layout.html` (`head(title)` and `topbar`).
`HeaderModelAdvice` puts `${header}` into every view, so a page needs no controller
change to get one. Logos are files on disk named `<church_id>.png` under
`app.storage.logo-dir` (default `./data/logos`, gitignored) — **never** mapped as a
static resource root, because `/logos/2.png` would let anyone enumerate the tenants.
`/church/logo` resolves the church from the principal instead, so there is no id in the
URL to tamper with. A missing file falls back to a shipped default.

## What is NOT built — the next work

1. **Bootstrap admin — blocking.** `churchuat` and `churchprod` have zero accounts and
   every page is behind a login, so a fresh deployment cannot be signed into at all
2. **Nothing checks permissions.** `@PreAuthorize` appears in one comment. The 275
   permission rows are loaded as authorities at sign-in and never consulted
3. **`created_user` / `updated_user` are never populated** — no `AuditorAware`
4. **Write-side tenant stamping** — reads are protected, writes are not. A form posting a
   hidden `churchId` would currently be trusted
5. **No CRUD screens at all** — dashboards are placeholders
6. **No church selector for platform staff.** A `saas_user` has `churchId = null`, so
   there is no way for them to enter one parish. Blocks the parish-priest module, whose
   writes need a `church_id`
7. **Logo upload** — the header displays a logo, but nothing uploads one. Files are placed
   by hand for now; upload belongs on the church edit screen
8. **Payment service layer** — the model is complete and tested, but nothing records a
   receipt, allocates, or voids
9. **No admin unlock screen** — locked accounts need SQL
10. **Receipt printing** and the Indian amount-in-words utility (lakh/crore, not millions)
11. **No CI**

Agreed order from here: **church selector** (touches the tenant context, so it stands
alone) → **Anbiyam module**, which is the template and carries `@PreAuthorize`,
`AuditorAware` and write-side stamping with it → **Parish Priest module**, which first
needs a migration making `to_date` nullable and adding `deleted_flag`, a
`PARISH_PRIEST` value in the `Resource` enum with its permission rows seeded, and an
entity, since that table is currently unmapped.

## Git

Remote is `https://github.com/santaigh/church-mgmt-app.git` — the rename is done.

Never commit `application-local.yml`, and never commit `data/` — it holds uploaded church
logos, which are runtime data, not source. Schema changes go in a migration, never straight
into the database — V16 exists solely to repair a constraint applied by hand.
