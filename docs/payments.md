# Monthly contributions

Each family agrees a monthly amount and pays it over time. Three awkward realities drive
the design:

1. A family pays **several months at once** — ₹500 covering January, February and March
2. A family pays **part of a month** — ₹100 against a ₹200 month, topped up later
3. The parish needs **arrears per family and per month**, in money and in months

## Why three tables

```mermaid
flowchart LR
    subgraph owed ["What is OWED"]
        PD["payment_due<br/><i>one row per family per month</i><br/>amount_due · amount_paid · status"]
    end
    subgraph link ["The LINK"]
        PA["payment_allocation<br/><i>how much of a payment<br/>went to which month</i>"]
    end
    subgraph paid ["What was PAID"]
        P["payment<br/><i>one row per receipt</i><br/>receipt_no · amount · mode"]
    end

    PD -->|"settled by"| PA
    P -->|"settles"| PA

    F["family<br/>monthly_amount<br/><i>the current rate</i>"] -.->|"snapshot when raised"| PD
    RS["receipt_sequence<br/><i>gapless numbering<br/>per church per year</i>"] -.->|"issues"| P
```

**A payments-only table cannot answer "who is behind".** A family that paid nothing has no
payment row — there is nothing to query. Arrears are about what *didn't* happen, and
absence is not something you can `SELECT`. `payment_due` supplies the list of expectations
to compare against.

**The allocation table exists because the relationship is genuinely many-to-many.** One
payment covers many months; one month is settled by many payments. Neither direction fits
as a column on the other side.

**`amount_due` is a snapshot, not a reference.** `family.monthly_amount` holds the *current*
rate; each due row copies the rate in force when it was raised. So raising a family from
₹200 to ₹300 affects future months only — past arrears are never retrospectively rewritten,
and a family is never chased for money it never owed.

## Worked example

Arulraj family, ₹200/month, nothing paid since January. On 15 March they hand over ₹500.

```
payment  R-2026-0001   15 Mar   ₹500  CASH
   │
   ├── payment_allocation → January 2026   ₹200   → PAID
   ├── payment_allocation → February 2026  ₹200   → PAID
   └── payment_allocation → March 2026     ₹100   → PARTIAL  (₹100 still owed)
```

One receipt, three months, the last one partial — and the receipt prints exactly those
three lines with their months.

## The two reports

**Arrears per family**

```sql
SELECT f.family_code, f.family_name,
       COUNT(*)                            AS pending_months,
       SUM(pd.amount_due - pd.amount_paid) AS pending_amount
FROM payment_due pd JOIN family f ON f.id = pd.family_id
WHERE pd.church_id = ? AND pd.status <> 'PAID' AND pd.deleted_flag = 0
GROUP BY f.id ORDER BY pending_amount DESC;
```

**Month by month across the parish**

```sql
SELECT due_year, due_month,
       SUM(amount_due)                 AS billed,
       SUM(amount_paid)                AS collected,
       SUM(amount_due - amount_paid)   AS pending
FROM payment_due WHERE church_id = ?
GROUP BY due_year, due_month ORDER BY due_year, due_month;
```

Both are also available as repository methods returning projections
(`FamilyArrears`, `MonthlyCollection`).

## Rules the database enforces

| Rule | Constraint |
|---|---|
| A month can never be paid more than it owes | `amount_paid <= amount_due` |
| A receipt cannot allocate more than was handed over | `allocated_amount <= amount` |
| Amounts are positive | `amount > 0`, `allocated_amount > 0` |
| Months are 1–12 | `due_month BETWEEN 1 AND 12` |
| Payment modes are known values | `CASH, UPI, CHEQUE, BANK_TRANSFER, CARD, OTHER` |
| Generating dues twice is harmless | `UNIQUE (family_id, due_year, due_month)` |
| Receipt numbers are unique per parish per year | `UNIQUE (church_id, receipt_year, receipt_no)` |

**The central invariant:** a due's `amount_paid` always equals the sum of its allocations.

All money is `DECIMAL(12,2)` — never floating point.

## Receipt numbering

Not `AUTO_INCREMENT`: that is global rather than per-church and leaves gaps whenever a
transaction rolls back. A parish auditor asking why receipt 41 is missing is a conversation
worth avoiding.

`receipt_sequence` holds a counter per church per year, read with a **pessimistic row lock**
while a receipt is issued, so two secretaries saving at the same instant cannot be handed
the same number. Numbering resets every January. Format: `R-2026-0042`.

Both St. Mary's and St. Joseph's hold their own `R-2026-0001` — receipt books are
per-parish.

## Voiding

A mistaken receipt is **never deleted**. It gets `status = VOID`, its allocations are
removed and the dues restored, while the original stays visible. A parish cash book with
rows silently removed is not a cash book. What the receipt used to cover is preserved in
`audit_log`.

Voided receipts are excluded from collection totals but remain in the record.

## Advance payments

A payment may exceed what is currently owed. The surplus is
`amount - allocated_amount` — advance credit, applied when future months are raised.
`PaymentRepository.findWithUnallocatedAmount` lists receipts holding one.

## Not yet built

The **data model is complete and tested; the service layer is not.** Nothing yet performs
an allocation, issues a receipt number, or voids a receipt. The repositories are shaped for
exactly those calls, including `findForUpdate` with its pessimistic lock.

Also outstanding:

- **The ₹50 minimum contribution is not enforced** — it sits in
  `app.payment.min-monthly-amount` awaiting the service that validates a rate change
- **Rate changes are not audited** — when a priest raises a family's amount, that should
  write `RECORD_UPDATED` with old and new values
