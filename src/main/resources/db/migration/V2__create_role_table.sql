-- ---------------------------------------------------------------------------
-- V2: Roles.
--
-- member.role_id and saas_user.role_id are NOT NULL but had no table to point
-- at, so this has to exist before either can hold a row.
--
-- Roles are rows rather than a Java enum, which keeps runtime-editable roles
-- possible later without a schema change. system_defined marks the six shipped
-- roles so the UI can stop anyone deleting them.
-- ---------------------------------------------------------------------------

CREATE TABLE role (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              VARCHAR(36)  NOT NULL,
    role_code         VARCHAR(30)  NOT NULL,
    role_name         VARCHAR(100) NOT NULL,
    role_level        VARCHAR(10)  NOT NULL COMMENT 'SAAS = platform-wide, APP = single church',
    description       VARCHAR(255) DEFAULT NULL,
    system_defined    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1 = shipped with the product, must not be deleted',
    record_status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_flag      TINYINT(1)   NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0,
    create_date       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_user      VARCHAR(100) DEFAULT NULL,
    last_updated_date DATETIME     DEFAULT NULL,
    updated_user      VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_uuid (uuid),
    UNIQUE KEY uk_role_code (role_code),
    KEY idx_role_level (role_level)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
