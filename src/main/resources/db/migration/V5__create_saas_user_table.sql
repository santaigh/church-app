-- ---------------------------------------------------------------------------
-- V5: Platform-level accounts.
--
-- No church_id, by design: SaaSSAdmin / SaaSAdmin / SaaSUser reach every church,
-- so scoping them to one would be wrong. That is exactly why they cannot live in
-- `member`, where church_id, family_id and anbiyam_id are all NOT NULL.
--
-- Mirrors the member login columns so both sign-in flows behave identically.
-- ---------------------------------------------------------------------------

CREATE TABLE saas_user (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                 VARCHAR(36)  NOT NULL,
    role_id              BIGINT       NOT NULL,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) DEFAULT NULL,
    email                VARCHAR(150) DEFAULT NULL,
    mobile               VARCHAR(20)  DEFAULT NULL,
    user_password        VARCHAR(100) NOT NULL COMMENT 'BCrypt hash -- never a plain value',
    password_flag        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0 = system default password, 1 = user has set their own',
    failed_attempt_count INT          NOT NULL DEFAULT 0,
    locked_at            DATETIME     NULL,
    last_login_at        DATETIME     NULL,
    password_changed_at  DATETIME     NULL,
    record_status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_flag         TINYINT(1)   NOT NULL DEFAULT 0,
    version              BIGINT       NOT NULL DEFAULT 0,
    create_date          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_user         VARCHAR(100) DEFAULT NULL,
    last_updated_date    DATETIME     DEFAULT NULL,
    updated_user         VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_saas_user_uuid (uuid),
    UNIQUE KEY uk_saas_user_email (email),
    UNIQUE KEY uk_saas_user_mobile (mobile),
    KEY fk_saas_user_role_id (role_id),
    KEY idx_saas_user_status (deleted_flag, record_status),
    CONSTRAINT fk_saas_user_role_id FOREIGN KEY (role_id) REFERENCES role (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
