-- ---------------------------------------------------------------------------
-- V13: Monthly contributions.
--
-- Three tables, because the relationship between money owed and money received
-- is genuinely many-to-many:
--
--   one payment  -> many months   (a family clears Jan, Feb and Mar at once)
--   one month    -> many payments (March part-paid now, topped up in April)
--
-- payment_due is what makes arrears answerable at all. A payments-only table
-- records what happened; "pending" is about what did NOT happen, and a family
-- that paid nothing in March simply has no payment row to find. payment_due
-- supplies the list of expectations to compare against.
--
-- It also freezes the rate: amount_due copies family.monthly_amount at the time
-- the due is raised. When a priest changes a family's amount, past dues keep the
-- figure that was actually owed, so arrears are never retrospectively rewritten.
-- ---------------------------------------------------------------------------

-- Per-family contribution rate. Varies by family income; dues_start_date stops
-- dues being raised for months before the family joined the register.
ALTER TABLE family
    ADD COLUMN monthly_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER phone,
    ADD COLUMN dues_start_date DATE NULL AFTER monthly_amount;


-- --------------------------------------------------------------- what is owed
CREATE TABLE payment_due (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    uuid              VARCHAR(36)    NOT NULL,
    church_id         BIGINT         NOT NULL,
    family_id         BIGINT         NOT NULL,
    due_year          SMALLINT       NOT NULL,
    due_month         TINYINT        NOT NULL COMMENT '1-12',
    due_date          DATE           NOT NULL,
    amount_due        DECIMAL(12, 2) NOT NULL COMMENT 'Snapshot of the rate in force when raised',
    amount_paid       DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT 'Always equals SUM of its allocations',
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | PARTIAL | PAID',
    remarks           VARCHAR(500)   NULL,
    record_status     VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    deleted_flag      TINYINT(1)     NOT NULL DEFAULT 0,
    version           BIGINT         NOT NULL DEFAULT 0,
    create_date       DATETIME       DEFAULT CURRENT_TIMESTAMP,
    created_user      VARCHAR(100)   DEFAULT NULL,
    last_updated_date DATETIME       DEFAULT NULL,
    updated_user      VARCHAR(100)   DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_due_uuid (uuid),
    -- Makes "generate dues for March" safe to press twice.
    UNIQUE KEY uk_payment_due_family_period (family_id, due_year, due_month),
    KEY idx_payment_due_church_period (church_id, due_year, due_month),
    KEY idx_payment_due_status (church_id, status, deleted_flag),
    CONSTRAINT fk_payment_due_church FOREIGN KEY (church_id) REFERENCES church (id),
    CONSTRAINT fk_payment_due_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT ck_payment_due_month CHECK (due_month BETWEEN 1 AND 12),
    CONSTRAINT ck_payment_due_amount CHECK (amount_due >= 0 AND amount_paid >= 0),
    -- Stops allocation logic ever applying more to a month than it owed.
    CONSTRAINT ck_payment_due_not_overpaid CHECK (amount_paid <= amount_due),
    CONSTRAINT ck_payment_due_status CHECK (status IN ('PENDING', 'PARTIAL', 'PAID'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ---------------------------------------------------------- money received
CREATE TABLE payment (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    uuid                  VARCHAR(36)    NOT NULL,
    church_id             BIGINT         NOT NULL,
    family_id             BIGINT         NOT NULL,
    receipt_no            VARCHAR(30)    NOT NULL,
    receipt_year          SMALLINT       NOT NULL COMMENT 'Numbering resets each January',
    receipt_date          DATE           NOT NULL,
    amount                DECIMAL(12, 2) NOT NULL COMMENT 'Total handed over',
    allocated_amount      DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT 'Applied to dues; the remainder is advance credit',
    payment_mode          VARCHAR(20)    NOT NULL,
    reference_no          VARCHAR(50)    NULL COMMENT 'Cheque number, UPI reference, etc.',
    received_by_member_id BIGINT         NULL COMMENT 'Who took the money',
    status                VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | VOID',
    void_reason           VARCHAR(500)   NULL,
    voided_date           DATETIME       NULL,
    voided_user           VARCHAR(100)   NULL,
    remarks               VARCHAR(500)   NULL,
    record_status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    deleted_flag          TINYINT(1)     NOT NULL DEFAULT 0,
    version               BIGINT         NOT NULL DEFAULT 0,
    create_date           DATETIME       DEFAULT CURRENT_TIMESTAMP,
    created_user          VARCHAR(100)   DEFAULT NULL,
    last_updated_date     DATETIME       DEFAULT NULL,
    updated_user          VARCHAR(100)   DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_uuid (uuid),
    -- Receipt numbers are unique per parish per year, so each church runs its own book.
    UNIQUE KEY uk_payment_receipt (church_id, receipt_year, receipt_no),
    KEY idx_payment_family (family_id, receipt_date),
    KEY idx_payment_church_date (church_id, receipt_date),
    KEY idx_payment_status (church_id, status),
    CONSTRAINT fk_payment_church FOREIGN KEY (church_id) REFERENCES church (id),
    CONSTRAINT fk_payment_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_payment_received_by FOREIGN KEY (received_by_member_id) REFERENCES member (id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_allocated CHECK (allocated_amount >= 0 AND allocated_amount <= amount),
    CONSTRAINT ck_payment_mode CHECK (payment_mode IN
        ('CASH', 'UPI', 'CHEQUE', 'BANK_TRANSFER', 'CARD', 'OTHER')),
    CONSTRAINT ck_payment_status CHECK (status IN ('ACTIVE', 'VOID'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ------------------------------------------------- which payment settled what
-- The join table that makes multi-month and partial payments representable.
-- Voiding a receipt deletes its allocations and restores the dues, so the
-- invariant always holds: SUM(allocated_amount) per due == payment_due.amount_paid.
-- What a voided receipt used to cover is preserved in audit_log.
CREATE TABLE payment_allocation (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    uuid              VARCHAR(36)    NOT NULL,
    church_id         BIGINT         NOT NULL,
    payment_id        BIGINT         NOT NULL,
    payment_due_id    BIGINT         NOT NULL,
    allocated_amount  DECIMAL(12, 2) NOT NULL,
    create_date       DATETIME       DEFAULT CURRENT_TIMESTAMP,
    created_user      VARCHAR(100)   DEFAULT NULL,
    last_updated_date DATETIME       DEFAULT NULL,
    updated_user      VARCHAR(100)   DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_allocation_uuid (uuid),
    -- One payment settles a given month once; topping up edits that row.
    UNIQUE KEY uk_payment_allocation (payment_id, payment_due_id),
    KEY idx_payment_allocation_due (payment_due_id),
    CONSTRAINT fk_payment_allocation_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_payment_allocation_due FOREIGN KEY (payment_due_id) REFERENCES payment_due (id),
    CONSTRAINT fk_payment_allocation_church FOREIGN KEY (church_id) REFERENCES church (id),
    CONSTRAINT ck_payment_allocation_amount CHECK (allocated_amount > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ------------------------------------------------------- receipt numbering
-- Not AUTO_INCREMENT: that is global rather than per-church, and leaves gaps when
-- a transaction rolls back. A parish auditor will ask why receipt 41 is missing.
-- This counter is row-locked while a receipt is issued, so numbering is gapless
-- and each parish keeps its own book, reset every January.
CREATE TABLE receipt_sequence (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    church_id         BIGINT       NOT NULL,
    sequence_year     SMALLINT     NOT NULL,
    prefix            VARCHAR(10)  NOT NULL DEFAULT 'R',
    last_number       INT          NOT NULL DEFAULT 0,
    create_date       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_user      VARCHAR(100) DEFAULT NULL,
    last_updated_date DATETIME     DEFAULT NULL,
    updated_user      VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_sequence (church_id, sequence_year),
    CONSTRAINT fk_receipt_sequence_church FOREIGN KEY (church_id) REFERENCES church (id),
    CONSTRAINT ck_receipt_sequence_number CHECK (last_number >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
