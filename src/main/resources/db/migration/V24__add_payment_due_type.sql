-- ---------------------------------------------------------------------------
-- V24: Tell an ordinary month apart from arrears carried in at cutover.
--
-- A parish adopting this app already has families who owe money -- sometimes
-- years of it. Reconstructing that history month by month would mean inventing
-- amounts nobody actually charged, so instead each family gets one opening
-- balance: everything owed before the system started, as a single line.
--
-- Without a type column that line is indistinguishable from a normal monthly
-- due, and every summary would fold two very different things together. A priest
-- looking at arrears wants to know which of it is "behind this year" and which is
-- "brought in from the old book".
--
-- Existing rows take the default and become MONTHLY, which is what they are.
--
-- Where an opening balance sits: the month *before* that family's
-- dues_start_date. That slot can never collide with a generated due -- nothing is
-- generated before a family starts paying -- so the unique key on
-- (family_id, due_year, due_month) needs no relaxing, and the row sorts first for
-- oldest-first allocation. Which is exactly where arrears carried forward belong.
-- ---------------------------------------------------------------------------

ALTER TABLE payment_due
    ADD COLUMN due_type VARCHAR(20) NOT NULL DEFAULT 'MONTHLY'
        COMMENT 'DueType enum name: MONTHLY or OPENING_BALANCE' AFTER due_date;

CREATE INDEX idx_payment_due_type ON payment_due (church_id, due_type, status);
