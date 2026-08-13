-- ---------------------------------------------------------------------------
-- V3: Families.
--
-- member.family_id is NOT NULL but had no table behind it, so no member row
-- could be created at all until this existed.
--
-- head_member_id is deliberately left WITHOUT a foreign key: member.family_id
-- is NOT NULL, so an FK in both directions would be circular and neither row
-- could be inserted first. It can be added later if the ordering is worth it.
-- ---------------------------------------------------------------------------

CREATE TABLE family (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              VARCHAR(36)  NOT NULL,
    church_id         BIGINT       NOT NULL,
    anbiyam_id        BIGINT       NOT NULL,
    family_code       VARCHAR(30)  NOT NULL,
    family_name       VARCHAR(150) NOT NULL,
    head_member_id    BIGINT       DEFAULT NULL COMMENT 'No FK: member.family_id is NOT NULL, so a two-way FK would be circular',
    address_line1     VARCHAR(255) DEFAULT NULL,
    address_line2     VARCHAR(255) DEFAULT NULL,
    city              VARCHAR(100) DEFAULT NULL,
    district          VARCHAR(100) DEFAULT NULL,
    state             VARCHAR(100) DEFAULT NULL,
    pincode           VARCHAR(10)  DEFAULT NULL,
    phone             VARCHAR(20)  DEFAULT NULL,
    record_status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_flag      TINYINT(1)   NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0,
    create_date       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_user      VARCHAR(100) DEFAULT NULL,
    last_updated_date DATETIME     DEFAULT NULL,
    updated_user      VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_family_uuid (uuid),
    -- Family codes need only be unique inside a parish, not across the platform.
    UNIQUE KEY uk_family_code_church (church_id, family_code),
    KEY fk_family_anbiyam_id (anbiyam_id),
    KEY idx_family_status (deleted_flag, record_status),
    CONSTRAINT fk_family_church_id FOREIGN KEY (church_id) REFERENCES church (id),
    CONSTRAINT fk_family_anbiyam_id FOREIGN KEY (anbiyam_id) REFERENCES anbiyam (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
