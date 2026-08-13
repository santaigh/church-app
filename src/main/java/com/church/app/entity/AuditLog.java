package com.church.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One audited event. Append-only: nothing in the application should ever update or
 * delete a row of this table.
 *
 * <p>Note this does NOT extend {@code AuditableEntity} -- an audit row has no
 * "last updated by", because it is never updated. It also holds no JPA associations:
 * {@code churchId}, {@code actorId} and {@code entityId} are plain values so the row
 * survives deletion of whatever it refers to.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    /** Null for platform-level actions that belong to no single church. */
    @Column(name = "church_id")
    private Long churchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    /** Denormalised copy -- stays readable after the actor's record is deleted. */
    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_code", length = 50)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_code", length = 30)
    private Operation operation;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome = "SUCCESS";

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** Denormalised human-readable identity, e.g. "Mary Arulraj" or "Receipt R-0442". */
    @Column(name = "entity_label")
    private String entityLabel;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    /** Matches the id in the application logs, so a row here leads to its log lines. */
    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime = LocalDateTime.now();

    public boolean isFailure() {
        return "FAILURE".equals(outcome);
    }
}
