-- ---------------------------------------------------------------------------
-- V14: Sample contribution data, covering the three cases that drove the design.
--
--   Arulraj    one payment clearing three months, the last one PARTIAL
--   Fernando   fully paid up
--   Devasagayam  nothing paid -- three months PENDING
--
-- Both St. Mary's and St. Joseph's issue a receipt numbered R-2026-0001,
-- demonstrating that receipt books are per-parish rather than global.
--
-- DEVELOPMENT DATA. Do not apply this migration to a production database.
-- ---------------------------------------------------------------------------

-- Per-family rates. Varying, because contributions are set by family income.
UPDATE family SET monthly_amount = 200.00, dues_start_date = '2026-01-01' WHERE id = 1; -- Arulraj
UPDATE family SET monthly_amount = 300.00, dues_start_date = '2026-01-01' WHERE id = 2; -- Devasagayam
UPDATE family SET monthly_amount = 150.00, dues_start_date = '2026-01-01' WHERE id = 3; -- Fernando
UPDATE family SET monthly_amount = 250.00, dues_start_date = '2026-01-01' WHERE id = 4; -- Pandian
UPDATE family SET monthly_amount = 100.00, dues_start_date = '2026-01-01' WHERE id = 5; -- Selvaraj
UPDATE family SET monthly_amount = 500.00, dues_start_date = '2026-01-01' WHERE id = 6; -- Irudayaraj

-- Dues for Jan-Mar 2026 for every family, at each family's own rate.
INSERT INTO payment_due (uuid, church_id, family_id, due_year, due_month, due_date,
                         amount_due, amount_paid, status, created_user)
SELECT UUID(), f.church_id, f.id, 2026, m.month_no,
       DATE(CONCAT('2026-', LPAD(m.month_no, 2, '0'), '-01')),
       f.monthly_amount, 0.00, 'PENDING', 'SYSTEM'
FROM family f
         CROSS JOIN (SELECT 1 AS month_no UNION ALL SELECT 2 UNION ALL SELECT 3) m
WHERE f.deleted_flag = 0;

-- Receipt counters: St. Mary's issued 2, St. Joseph's 1, Our Lady of Lourdes none.
INSERT INTO receipt_sequence (church_id, sequence_year, prefix, last_number, created_user) VALUES
    (1, 2026, 'R', 2, 'SYSTEM'),
    (2, 2026, 'R', 1, 'SYSTEM'),
    (3, 2026, 'R', 0, 'SYSTEM');

-- ---------------------------------------------------------------- payments
-- Arulraj: Rs.500 on 15 March, clearing Jan and Feb in full and part of March.
INSERT INTO payment (uuid, church_id, family_id, receipt_no, receipt_year, receipt_date,
                     amount, allocated_amount, payment_mode, received_by_member_id,
                     remarks, created_user)
VALUES ('a1000000-0000-4000-8000-000000000001', 1, 1, 'R-2026-0001', 2026, '2026-03-15',
        500.00, 500.00, 'CASH', 2, 'Cleared Jan and Feb, part of March', 'SYSTEM');

-- Fernando: Rs.450 by UPI on 10 March, fully settling Jan-Mar.
INSERT INTO payment (uuid, church_id, family_id, receipt_no, receipt_year, receipt_date,
                     amount, allocated_amount, payment_mode, reference_no,
                     received_by_member_id, created_user)
VALUES ('a1000000-0000-4000-8000-000000000002', 1, 3, 'R-2026-0002', 2026, '2026-03-10',
        450.00, 450.00, 'UPI', 'UPI-8842910', 2, 'SYSTEM');

-- Pandian, at a different parish, holding its own R-2026-0001.
INSERT INTO payment (uuid, church_id, family_id, receipt_no, receipt_year, receipt_date,
                     amount, allocated_amount, payment_mode, received_by_member_id, created_user)
VALUES ('a1000000-0000-4000-8000-000000000003', 2, 4, 'R-2026-0001', 2026, '2026-02-05',
        250.00, 250.00, 'CASH', 8, 'SYSTEM');

-- ------------------------------------------------------------- allocations
-- Arulraj: 200 + 200 + 100 across January, February, March.
INSERT INTO payment_allocation (uuid, church_id, payment_id, payment_due_id, allocated_amount, created_user)
SELECT UUID(), 1, p.id, d.id,
       CASE d.due_month WHEN 3 THEN 100.00 ELSE 200.00 END, 'SYSTEM'
FROM payment p
         JOIN payment_due d ON d.family_id = 1 AND d.due_year = 2026 AND d.due_month IN (1, 2, 3)
WHERE p.uuid = 'a1000000-0000-4000-8000-000000000001';

-- Fernando: 150 to each of the three months.
INSERT INTO payment_allocation (uuid, church_id, payment_id, payment_due_id, allocated_amount, created_user)
SELECT UUID(), 1, p.id, d.id, 150.00, 'SYSTEM'
FROM payment p
         JOIN payment_due d ON d.family_id = 3 AND d.due_year = 2026 AND d.due_month IN (1, 2, 3)
WHERE p.uuid = 'a1000000-0000-4000-8000-000000000002';

-- Pandian: the whole 250 to January only.
INSERT INTO payment_allocation (uuid, church_id, payment_id, payment_due_id, allocated_amount, created_user)
SELECT UUID(), 2, p.id, d.id, 250.00, 'SYSTEM'
FROM payment p
         JOIN payment_due d ON d.family_id = 4 AND d.due_year = 2026 AND d.due_month = 1
WHERE p.uuid = 'a1000000-0000-4000-8000-000000000003';

-- Roll the allocations up onto the dues, so amount_paid always equals the sum of
-- its allocations -- the invariant the whole model rests on.
UPDATE payment_due d
SET d.amount_paid = COALESCE((SELECT SUM(a.allocated_amount)
                              FROM payment_allocation a
                              WHERE a.payment_due_id = d.id), 0.00);

UPDATE payment_due
SET status = CASE
                 WHEN amount_paid >= amount_due THEN 'PAID'
                 WHEN amount_paid > 0 THEN 'PARTIAL'
                 ELSE 'PENDING'
    END;
