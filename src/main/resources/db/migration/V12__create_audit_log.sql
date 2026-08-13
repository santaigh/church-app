-- ---------------------------------------------------------------------------
-- V12: The audit trail.
--
-- Answers "who changed what, when, and what did it say before" -- which the
-- created_user / updated_user columns cannot, because they only ever hold the
-- LAST edit and say nothing about the previous value or about deletions.
--
-- Three deliberate departures from the conventions used elsewhere in this schema:
--
--  1. APPEND-ONLY. No version, no deleted_flag, no updated_user, no
--     last_updated_date. An audit row is a statement of fact about a past event;
--     nothing should ever edit it. An audit log an administrator can quietly
--     rewrite proves nothing.
--
--  2. NO FOREIGN KEYS to member / saas_user / church. Audit rows must outlive the
--     records they describe -- deleting a member must not erase the evidence of
--     what they did, and an FK would either block the delete or cascade the
--     history away. actor_name and entity_label are denormalised copies for the
--     same reason: they stay readable after the referenced row is gone.
--
--  3. NO record_status. There is no such thing as an inactive audit entry.
--
-- correlation_id ties a row here back to the application log lines for the same
-- request, so a business event can be traced to its stack trace and vice versa.
-- ---------------------------------------------------------------------------

CREATE TABLE audit_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    uuid            VARCHAR(36)  NOT NULL,

    -- NULL for platform-level actions that belong to no single church.
    church_id       BIGINT       NULL,

    -- Who did it. No FK, on purpose (see note 2).
    actor_type      VARCHAR(20)  NOT NULL COMMENT 'MEMBER | SAAS_USER | SYSTEM | ANONYMOUS',
    actor_id        BIGINT       NULL,
    actor_name      VARCHAR(150) NULL COMMENT 'Denormalised: stays readable after the actor is deleted',

    -- What happened.
    event_type      VARCHAR(50)  NOT NULL COMMENT 'AuditEventType enum name',
    resource_code   VARCHAR(50)  NULL COMMENT 'Resource enum name, where applicable',
    operation_code  VARCHAR(30)  NULL COMMENT 'Operation enum name, where applicable',
    outcome         VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS | FAILURE',

    -- What it happened to.
    entity_type     VARCHAR(50)  NULL COMMENT 'Table or entity name, e.g. member',
    entity_id       BIGINT       NULL,
    entity_label    VARCHAR(255) NULL COMMENT 'Denormalised human-readable identity of the record',

    -- Before and after. TEXT rather than JSON so the mapping stays a plain String;
    -- can migrate to a JSON column later if querying inside the payload is needed.
    old_value       TEXT         NULL,
    new_value       TEXT         NULL,
    description     VARCHAR(500) NULL,

    -- Where it came from.
    ip_address      VARCHAR(45)  NULL COMMENT 'Sized for IPv6',
    user_agent      VARCHAR(255) NULL,
    correlation_id  VARCHAR(36)  NULL COMMENT 'Matches the id in the application logs',

    event_time      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_log_uuid (uuid),
    -- "What happened in this parish, most recent first" -- the main screen query.
    KEY idx_audit_church_time (church_id, event_time),
    -- "Show me the history of this member".
    KEY idx_audit_entity (entity_type, entity_id, event_time),
    -- "What has this user been doing".
    KEY idx_audit_actor (actor_type, actor_id, event_time),
    KEY idx_audit_event_type (event_type, event_time),
    KEY idx_audit_correlation (correlation_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
