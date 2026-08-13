-- ---------------------------------------------------------------------------
-- V1: The original five tables.
--
-- These were created by hand, outside Flyway, and V1 existed only as a BASELINE
-- marker recording that fact. That worked on the machine where they already
-- existed -- and failed everywhere else: a fresh database went straight to V2,
-- and V3 died on "Failed to open the referenced table 'church'".
--
-- This script reproduces them exactly as they were, so any empty database can be
-- built from migrations alone.
--
-- On a database that was already baselined, Flyway treats version 1 as applied
-- and skips this file, so nothing is recreated and no data is touched.
--
-- Later migrations extend these tables and must not be folded in here:
--   V4  adds the login columns and unique keys to `member`
--   V16 adds the one-profile-per-member constraint to `member_ext`
-- ---------------------------------------------------------------------------

CREATE TABLE church (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    uuid               VARCHAR(36)    NOT NULL,
    church_name        VARCHAR(150)   NOT NULL,
    category_id        VARCHAR(150)   NOT NULL COMMENT 'ChurchCategory enum: STATION | SUBSTATION',
    parent_church_id   BIGINT         DEFAULT NULL COMMENT 'A substation points at its parent parish',
    parent_church_name VARCHAR(255)   DEFAULT NULL,
    location           VARCHAR(255)   DEFAULT NULL,
    latitude           DECIMAL(10, 7) DEFAULT NULL,
    longitude          DECIMAL(10, 7) DEFAULT NULL,
    diocese            VARCHAR(150)   DEFAULT NULL,
    parish_priest      VARCHAR(150)   DEFAULT NULL,
    address_line1      VARCHAR(255)   DEFAULT NULL,
    address_line2      VARCHAR(255)   DEFAULT NULL,
    city               VARCHAR(100)   DEFAULT NULL,
    district           VARCHAR(100)   DEFAULT NULL,
    state              VARCHAR(100)   DEFAULT NULL,
    country            VARCHAR(100)   DEFAULT 'India',
    pincode            VARCHAR(10)    DEFAULT NULL,
    phone              VARCHAR(20)    DEFAULT NULL,
    email              VARCHAR(150)   DEFAULT NULL,
    established_date   DATE           DEFAULT NULL,
    record_status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    deleted_flag       TINYINT(1)     NOT NULL DEFAULT 0,
    version            BIGINT         NOT NULL DEFAULT 0,
    create_date        DATETIME       DEFAULT CURRENT_TIMESTAMP,
    created_user       VARCHAR(100)   DEFAULT NULL,
    last_updated_date  DATETIME       DEFAULT NULL,
    updated_user       VARCHAR(100)   DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_church_uuid (uuid),
    KEY idx_church_status (deleted_flag, record_status),
    KEY idx_church_parent (parent_church_id),
    KEY idx_church_category (category_id),
    CONSTRAINT fk_church_parent FOREIGN KEY (parent_church_id) REFERENCES church (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- anbiyam.head_member_id references member, and member.anbiyam_id references
-- anbiyam. The pair is circular, so the table is created without that key and it
-- is added by ALTER once member exists, further down.
CREATE TABLE anbiyam (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              VARCHAR(36)  NOT NULL,
    church_id         BIGINT       NOT NULL,
    anbiyam_name      VARCHAR(150) NOT NULL COMMENT 'Usually Tamil script -- hence utf8mb4',
    head_member_id    BIGINT       DEFAULT NULL COMMENT 'The animator',
    area_description  VARCHAR(255) DEFAULT NULL,
    record_status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_flag      TINYINT(1)   NOT NULL DEFAULT 0,
    active_flag       TINYINT(1)   NOT NULL DEFAULT 0,
    create_date       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_user      VARCHAR(100) DEFAULT NULL,
    last_updated_date DATETIME     DEFAULT NULL,
    updated_user      VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_anbiyam_uuid (uuid),
    KEY fk_anbiyam_head (head_member_id),
    KEY fk_anbiyam_church_id (church_id),
    CONSTRAINT fk_anbiyam_church_id FOREIGN KEY (church_id) REFERENCES church (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- family_id and role_id carry no foreign key at this point: neither table exists
-- yet. V3 creates `family`, V2 creates `role`, and V4 adds both constraints.
CREATE TABLE member (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              VARCHAR(36)  NOT NULL,
    church_id         BIGINT       NOT NULL,
    family_id         BIGINT       NOT NULL,
    anbiyam_id        BIGINT       NOT NULL,
    role_id           BIGINT       NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    middle_name       VARCHAR(100) DEFAULT NULL,
    last_name         VARCHAR(100) DEFAULT NULL,
    member_password   VARCHAR(100) NOT NULL COMMENT 'BCrypt hash',
    gender            VARCHAR(10)  NOT NULL,
    date_of_birth     DATE         DEFAULT NULL,
    mobile            VARCHAR(20)  DEFAULT NULL,
    alternate_mobile  VARCHAR(20)  DEFAULT NULL,
    email             VARCHAR(150) DEFAULT NULL,
    catagory          VARCHAR(100) DEFAULT NULL COMMENT 'Spelling as originally created; mapped to Member.category',
    remarks           VARCHAR(500) DEFAULT NULL,
    record_status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_flag      TINYINT(1)   NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0,
    create_date       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_user      VARCHAR(100) DEFAULT NULL,
    last_updated_date DATETIME     DEFAULT NULL,
    updated_user      VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_uuid (uuid),
    KEY fk_member_church_id (church_id),
    KEY fk_member_anbiyam_id (anbiyam_id),
    CONSTRAINT fk_member_church_id FOREIGN KEY (church_id) REFERENCES church (id),
    CONSTRAINT fk_member_anbiyam_id FOREIGN KEY (anbiyam_id) REFERENCES anbiyam (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Now that member exists, close the circular reference.
ALTER TABLE anbiyam
    ADD CONSTRAINT fk_anbiyam_head FOREIGN KEY (head_member_id) REFERENCES member (id);


CREATE TABLE member_ext (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                 VARCHAR(36)  NOT NULL,
    church_id            BIGINT       NOT NULL,
    family_id            BIGINT       NOT NULL,
    member_id            BIGINT       NOT NULL,
    blood_group          VARCHAR(5)   DEFAULT NULL,
    marital_status       VARCHAR(20)  DEFAULT NULL,
    address_line1        VARCHAR(255) DEFAULT NULL,
    address_line2        VARCHAR(255) DEFAULT NULL,
    city                 VARCHAR(100) DEFAULT NULL,
    district             VARCHAR(100) DEFAULT NULL,
    state                VARCHAR(100) DEFAULT NULL,
    pincode              VARCHAR(10)  DEFAULT NULL,
    occupation           VARCHAR(100) DEFAULT NULL,
    education            VARCHAR(100) DEFAULT NULL,
    native_place         VARCHAR(150) DEFAULT NULL,
    baptism_date         DATE         DEFAULT NULL,
    baptism_place        VARCHAR(150) DEFAULT NULL,
    holy_communion_date  DATE         DEFAULT NULL,
    holy_communion_place VARCHAR(150) DEFAULT NULL,
    confirmation_date    DATE         DEFAULT NULL,
    confirmation_place   VARCHAR(150) DEFAULT NULL,
    marriage_date        DATE         DEFAULT NULL,
    marriage_place       VARCHAR(150) DEFAULT NULL,
    record_status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_flag         TINYINT(1)   NOT NULL DEFAULT 0,
    version              BIGINT       NOT NULL DEFAULT 0,
    create_date          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_user         VARCHAR(100) DEFAULT NULL,
    last_updated_date    DATETIME     DEFAULT NULL,
    updated_user         VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_ext_uuid (uuid),
    KEY fk_member_ext_church_id (church_id),
    KEY fk_member_ext_id (member_id),
    CONSTRAINT fk_member_ext_church_id FOREIGN KEY (church_id) REFERENCES church (id),
    CONSTRAINT fk_member_ext_id FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE parish_priest (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              VARCHAR(36)  NOT NULL,
    priest_name       VARCHAR(100) NOT NULL,
    priest_last_place VARCHAR(100) DEFAULT NULL,
    from_date         DATETIME     NOT NULL,
    to_date           DATETIME     NOT NULL,
    create_date       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_user      VARCHAR(100) DEFAULT NULL,
    last_updated_date DATETIME     DEFAULT NULL,
    updated_user      VARCHAR(100) DEFAULT NULL,
    active_flag       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 = the priest currently serving',
    church_id         BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parish_priest_uuid (uuid),
    KEY fk_parish_priest_church_id (church_id),
    CONSTRAINT fk_parish_priest_church_id FOREIGN KEY (church_id) REFERENCES church (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
