# Church Management App

Multi-tenant parish administration platform. One deployment serves many churches; each
parish sees only its own data.

Spring Boot 4.1 · Java 17 · Thymeleaf · MySQL 8 · Gradle · Flyway

## Documentation

Full documentation lives in [docs/](docs/):

| Document | Covers |
|---|---|
| [Overview deck](docs/church-app-overview.pptx) | 10-slide visual overview |
| [architecture.md](docs/architecture.md) | Layering, request flow, technology |
| [database-schema.md](docs/database-schema.md) | ERD, tables, tenant scoping |
| [security.md](docs/security.md) | Login, RBAC, passwords, lockout, audit |
| [payments.md](docs/payments.md) | Dues, receipts, allocation, arrears |

## Running locally

Requires **Java 17** and **MySQL 8** with a schema named `churchnew`.

```sql
CREATE DATABASE churchnew CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Database credentials are **not** stored in the repository. Supply them either through the
environment:

```bash
set DB_USERNAME=root
set DB_PASSWORD=yourpassword
gradlew.bat bootRun
```

…or by creating `src/main/resources/application-local.yml`, which is gitignored:

```yaml
spring:
  datasource:
    username: root
    password: yourpassword
```

Flyway creates and populates the schema on first start.

```bash
gradlew.bat build      # compile, migrate, run tests
gradlew.bat bootRun    # http://localhost:8080
```

| URL | Purpose |
|---|---|
| `/login` | Parish sign-in (members) |
| `/saas/login` | Platform sign-in (SaaS staff) |
| `/actuator/health` | Health check |

## Environments

| Profile | File | Purpose |
|---|---|---|
| `dev` | `application-dev.yml` | A developer's machine. **The only profile that loads sample data.** |
| `uat` | `application-uat.yml` | Acceptance testing. Configured like production |
| `prod` | `application-prod.yml` | Production |

```bash
SPRING_PROFILES_ACTIVE=uat  java -jar church-app.jar
```

Shared settings live in `application.yml`; only what genuinely differs is in a profile
file. Required environment variables for `uat` and `prod`:

| Variable | Notes |
|---|---|
| `DB_URL` | Full JDBC URL, including `characterEncoding=UTF-8` for Tamil text |
| `DB_USERNAME`, `DB_PASSWORD` | No defaults exist — startup fails loudly if missing |
| `APP_DEFAULT_PASSWORD`, `APP_RESET_PASSWORD` | Override the committed values, which are public |

## Sample data

Migrations live in two places:

- **`db/migration`** — schema and reference data (roles, permissions). Applied everywhere.
- **`db/seed`** — `V7` and `V14`, which insert three fictional churches with members,
  families, Anbiyams and receipts. **Listed only in `application-dev.yml`.**

So sample data cannot reach UAT or production — not by policy, but because those profiles
never look in that directory. Verified against a fresh schema: reference data arrives
(6 roles, 275 permissions) with zero churches, members or payments.

Every sample account uses the password `Welcome123$` — see
[docs/security.md](docs/security.md#sample-accounts).

## Project status

Login, RBAC, password lifecycle, lockout, audit and the full payment data model are built
and tested. Screens for members, families, Anbiyams and churches are not yet written, and
**tenant filtering is not yet enforced** — see
[docs/database-schema.md](docs/database-schema.md#tenant-isolation).
