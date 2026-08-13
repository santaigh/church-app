-- ---------------------------------------------------------------------------
-- V9: The role -> permission mapping. This is the "dynamic" half of dynamic RBAC.
--
-- The vocabulary (Resource, Operation) lives in Java enums, because a new screen
-- or a new operation can only exist once a developer has written it. The mapping
-- lives here, because who may do what has to be changeable without a redeploy.
--
-- A row means the permission is GRANTED. There is deliberately no `allowed`
-- column and no `deleted_flag`: revoking hard-deletes the row. Soft delete would
-- be a trap here, because a revoked row still occupies the unique key
-- (role_id, resource_code, operation_code), so re-granting the same permission
-- later would fail with a duplicate-key error. Who changed a permission and when
-- belongs in the audit log, not in tombstone rows.
--
-- operation_code is CHECK-constrained so the database rejects a typo even though
-- the values come from an enum. resource_code deliberately is not: the resource
-- list grows as modules are added through Phase 1, and that should not require a
-- migration per screen.
-- ---------------------------------------------------------------------------

CREATE TABLE role_permission (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              VARCHAR(36)  NOT NULL,
    role_id           BIGINT       NOT NULL,
    resource_code     VARCHAR(50)  NOT NULL COMMENT 'Resource enum name',
    operation_code    VARCHAR(30)  NOT NULL COMMENT 'Operation enum name',
    record_status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version           BIGINT       NOT NULL DEFAULT 0,
    create_date       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    created_user      VARCHAR(100) DEFAULT NULL,
    last_updated_date DATETIME     DEFAULT NULL,
    updated_user      VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission_uuid (uuid),
    UNIQUE KEY uk_role_permission (role_id, resource_code, operation_code),
    KEY idx_role_permission_role (role_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    CONSTRAINT ck_role_permission_operation CHECK (operation_code IN
        ('ADD', 'VIEW', 'EDIT', 'DELETE', 'ACTIVATE_DEACTIVATE', 'EXPORT'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
