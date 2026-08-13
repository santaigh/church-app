# Church App — Documentation

Multi-tenant parish administration platform. One deployment serves many churches;
each church sees only its own data.

| Document | Covers |
|---|---|
| **[church-app-overview.pptx](church-app-overview.pptx)** | **10-slide visual overview** — diagrams as editable PowerPoint shapes |
| [architecture.md](architecture.md) | System overview, layering, request flow, technology choices |
| [database-schema.md](database-schema.md) | ERD, table-by-table reference, tenant scoping, known inconsistencies |
| [security.md](security.md) | The two login chains, RBAC model, password lifecycle, lockout, audit |
| [payments.md](payments.md) | Monthly contributions: dues, receipts, allocation, arrears |

`slides/` holds a PNG of each deck slide, for embedding or quick viewing without
PowerPoint. The Markdown files carry the same diagrams as Mermaid, which GitHub and most
IDEs render inline.

## Current state

**Built and verified**

- Login for parish members and platform staff, on two independent security chains
- Dynamic RBAC: roles and permissions as data, 275 seeded permission rows
- Password lifecycle: forced first-login change, self-service reset, lockout
- Audit trail with correlation IDs linking rows to application log lines
- Complete data model for members, families, Anbiyams and monthly contributions

**Not yet built**

- Any CRUD screens — the dashboards are placeholders
- **Tenant filtering is not enforced** (see [database-schema.md](database-schema.md#tenant-isolation)).
  The `church_id` columns exist but no query applies them automatically.
- Service layer for payments: recording receipts, allocating across dues, voiding
- Member, Family, Anbiyam and Church management modules

## Running it

```bash
./gradlew build          # compiles, runs migrations, runs 49 tests
./gradlew bootRun        # starts on http://localhost:8080
```

Requires MySQL on `localhost:3306` with a schema named `churchnew`. Connection settings
are in `src/main/resources/application.yml`.

| URL | Purpose |
|---|---|
| `/login` | Parish sign-in (members) |
| `/saas/login` | Platform sign-in (SaaS staff) |
| `/actuator/health` | Health check |

Sample accounts all use the password `Welcome123$`. See
[security.md](security.md#sample-accounts).
