package com.church.app.repository;

import com.church.app.entity.ActorType;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read and append only.
 *
 * <p>{@code JpaRepository} inherits {@code delete} and {@code save}-as-update; neither
 * should ever be called on this table. Nothing in the application does, and the intent
 * is documented on {@link AuditLog}.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** "What happened in this parish", most recent first. */
    Page<AuditLog> findByChurchIdOrderByEventTimeDesc(Long churchId, Pageable pageable);

    /** Platform-level events, which belong to no church. */
    Page<AuditLog> findByChurchIdIsNullOrderByEventTimeDesc(Pageable pageable);

    /** "Show me the history of this record". */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByEventTimeDesc(String entityType, Long entityId);

    /** "What has this user been doing". */
    Page<AuditLog> findByActorIdOrderByEventTimeDesc(Long actorId, Pageable pageable);

    List<AuditLog> findByEventTypeAndEventTimeAfterOrderByEventTimeDesc(
            AuditEventType eventType, LocalDateTime after);

    /** Ties audit rows back to the application log lines for the same request. */
    List<AuditLog> findByCorrelationId(String correlationId);

    long countByEventType(AuditEventType eventType);

    /**
     * Backs the rate limit on "forgot password".
     *
     * <p>Uses the audit trail as the counter rather than a separate table: the events are
     * already being recorded, and a limit derived from the same record an auditor reads
     * cannot drift away from it.
     */
    long countByActorTypeAndActorIdAndEventTypeAndEventTimeAfter(
            ActorType actorType, Long actorId, AuditEventType eventType, LocalDateTime after);
}
