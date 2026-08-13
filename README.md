# Church App

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

## Sample data

Migrations `V7` and `V14` seed three fictional churches with members, families, Anbiyams
and contributions, for development only. Every sample account uses the password
`Welcome123$` — see [docs/security.md](docs/security.md#sample-accounts).

> **These migrations must not run against a production database.** Separating them via
> profile-specific `spring.flyway.locations` is an outstanding task.

## Project status

Login, RBAC, password lifecycle, lockout, audit and the full payment data model are built
and tested. Screens for members, families, Anbiyams and churches are not yet written, and
**tenant filtering is not yet enforced** — see
[docs/database-schema.md](docs/database-schema.md#tenant-isolation).
