# Modules

What the application does today, screen by screen. For *why* a thing works the way it
does, the reasoning lives in [payments.md](payments.md), [security.md](security.md) and
[database-schema.md](database-schema.md).

## Getting in

Two independent sign-in chains, by design. Credentials valid on one are refused by the
other.

| Chain | Reads | Lands on |
|---|---|---|
| `/login` | `member` | the parish dashboard |
| `/saas/login` | `saas_user` | the parish selector |

A first sign-in always goes through a forced password change, because every account is
created on a system-assigned password (`password_flag = 0`).

## Parish selector — platform staff only

A `saas_user` has no church of its own, which is what lets it reach every parish. The
selector is how it enters one.

- Left: every **station**, with its town, because two parishes may share a name
- Right: that parish in full — clergy serving, counts, substations, address
- **Enter this parish** narrows the account to it for the rest of the session

Entering is not cosmetic. The tenant scope follows the selection, so reads and writes
behave exactly as a parish user's would. Returning to the selector leaves the parish.
Both directions are written to the audit log.

**Substations are never enterable.** A substation is a place of worship under a station
and holds no members, families, anbiyam or clergy of its own.

## Parish dashboard

Counts for members, families, anbiyam and substations — the first three link to their
lists, and only where the role may open them. Below: clergy serving now, with a warning
when no parish priest is recorded, and the parish's substations.

## Anbiyam

List, add, edit, soft delete. Each row shows its animator with the member number, and how
many families and members belong to it. Clicking the name opens that anbiyam's members.

- The **animator must be a member of that anbiyam** — refused three ways: another parish's
  member, another anbiyam's member, and anyone at all before the anbiyam exists to have
  members
- Delete is refused while families or members still point at it
- Two anbiyam in one parish may not share a name

## Parish Priest and clergy

An appointment history rather than a roster. Three roles share the table: **Parish
Priest**, **Assistant Priest** and **Brother** (a final-year seminarian, not ordained).

- **An empty end date means currently serving.** That is the only definition
- Appointing a parish priest **closes the previous one** automatically; assistants do not
  close each other, because a parish may have several
- A gap between postings is allowed and flagged, not blocked — real parishes have them
- **Only SaaSSAdmin and SaaSAdmin may appoint.** Parish staff see who serves them; an
  appointment is a diocese-level act

## Members

List, view, add, edit, soft delete — paged and searched in the database, because a parish
of 600 families runs to a few thousand members.

- Families stay together and read in household order: **head, spouse, children, then a
  parent living with them**
- **One head per family**, held in step with `family.head_member_id` so the two can never
  disagree. Naming a new head demotes the previous one in the same action
- The member page carries **everything**, including `member_ext` — blood group, marital
  status, address, occupation, education, native place and the four sacraments — editable,
  creating its row on first save
- **Age is derived** from the date of birth wherever shown, and stored nowhere
- Adding a member **creates an account**: credentials live on `member`, so a new row gets
  a role and the default password with a forced change at first sign-in
- **Role assignment is guarded** — an AppAdmin may grant any parish role except AppSA, and
  nobody edits their own role

## Families

Read-only. Name with its number, code, anbiyam, head of family and member count. Clicking
a family opens its members.

A family may sit **headless** between one head leaving and another being named; it shows
as `—` rather than an error.

## Payments

The money rules are in [payments.md](payments.md). The screens:

| Screen | Who | Does |
|---|---|---|
| **Collect payment** | AppUser and up | Find a family, see what is owed, record and print |
| **Receipt** | anyone with VIEW | The slip, the months it settled, and the amount in words |
| **Print** | anyone with VIEW | 58mm thermal layout, bilingual, no navigation |
| **Payments list** | anyone with VIEW | Every receipt, paged |
| **Generate dues** | AppUser and up | One month's contribution for every family, idempotent |
| **Opening balances** | AppAdmin and up | Arrears carried in at cutover, one figure per family |
| **Void and reissue** | AppSA only | Cancel a wrong receipt and issue the correct one |

Money settles the **oldest month first**, always, and a collector cannot override it.
Anything beyond the arrears runs forward, generating months at the family's own rate.

## Across every screen

**Permissions are enforced, not decorated.** The menu is built from the account's own
authorities, and every controller method carries its own check — so a hidden link that is
typed as a URL is refused with 403 rather than opened.

**Tenant isolation.** The parish comes from the request's scope, never from the form. No
screen has a church field; a posted `churchId` is ignored.

**Auditing.** Sign-ins, failures, lockouts, entering and leaving a parish, and every
create, update and delete are written to `audit_log` with who, when and where.
`created_user` and `updated_user` are filled automatically.

**Dates read day-first** — `dd-MM-yyyy` — from one place. Date pickers still carry ISO
values, which is an HTML requirement, and browsers render them in the operating system's
locale.

**Tables become cards below 640px**, everywhere rather than only on payments, so every
module inherits one rule instead of two that drift.

**English now, Tamil later.** Screen text comes from `messages.properties`, so a second
language is one more file rather than a pass over every page. Member and anbiyam names are
data — already Tamil, already utf8mb4.

## Not built

- **Bootstrap admin** — `churchuat` and `churchprod` have no accounts, so a fresh
  deployment cannot be signed into
- **Excel import** — designed, not built. It must call the services rather than the
  repositories, or it becomes a second, weaker set of rules
- **Family add and edit** — the list is read-only
- **Reports** — daily collection, arrears, family statement
- **Logo upload** — the header displays one; files are placed by hand
- **Admin unlock screen** — a locked account still needs SQL
- **CI**
