package com.church.app.service;

import com.church.app.entity.ActorType;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.AuditLog;
import com.church.app.entity.Operation;
import com.church.app.entity.Resource;
import com.church.app.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The single place audit entries are created.
 *
 * <p>Everything that mutates data should go through a service method, and that service
 * method should call this. Keeping one seam is what makes it possible to add auditing to
 * a new module without hunting through controllers.
 *
 * <p>Usage:
 * <pre>
 * auditService.event(AuditEventType.RECORD_UPDATED)
 *             .actorMember(principal.getUserId(), principal.getFullName(), principal.getChurchId())
 *             .on("member", member.getId(), member.getDisplayName())
 *             .changed(oldMobile, newMobile)
 *             .describe("Mobile number updated")
 *             .save();
 * </pre>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final int MAX_VALUE_LENGTH = 4000;
    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final AuditWriter auditWriter;

    public AuditService(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    /** Starts a new audit entry. Nothing is written until {@link Entry#save()} is called. */
    public Entry event(AuditEventType eventType) {
        return new Entry(eventType);
    }

    /** Fluent builder for one audit row. */
    public final class Entry {

        private final AuditLog entry = new AuditLog();

        private Entry(AuditEventType eventType) {
            entry.setUuid(UUID.randomUUID().toString());
            entry.setEventType(eventType);
            entry.setEventTime(LocalDateTime.now());
            entry.setActorType(ActorType.SYSTEM);
            entry.setCorrelationId(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
            populateFromCurrentRequest();
        }

        public Entry actorMember(Long memberId, String name, Long churchId) {
            entry.setActorType(ActorType.MEMBER);
            entry.setActorId(memberId);
            entry.setActorName(name);
            entry.setChurchId(churchId);
            return this;
        }

        public Entry actorSaasUser(Long saasUserId, String name) {
            entry.setActorType(ActorType.SAAS_USER);
            entry.setActorId(saasUserId);
            entry.setActorName(name);
            // Deliberately leaves churchId null: a platform action belongs to no parish.
            return this;
        }

        /** For events that happen before identity is established, such as a failed login. */
        public Entry actorAnonymous(String attemptedIdentifier) {
            entry.setActorType(ActorType.ANONYMOUS);
            entry.setActorName(attemptedIdentifier);
            return this;
        }

        public Entry inChurch(Long churchId) {
            entry.setChurchId(churchId);
            return this;
        }

        public Entry on(String entityType, Long entityId, String label) {
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setEntityLabel(truncate(label, 255));
            return this;
        }

        public Entry permission(Resource resource, Operation operation) {
            entry.setResource(resource);
            entry.setOperation(operation);
            return this;
        }

        public Entry changed(String oldValue, String newValue) {
            entry.setOldValue(truncate(oldValue, MAX_VALUE_LENGTH));
            entry.setNewValue(truncate(newValue, MAX_VALUE_LENGTH));
            return this;
        }

        public Entry describe(String description) {
            entry.setDescription(truncate(description, 500));
            return this;
        }

        public Entry failed() {
            entry.setOutcome("FAILURE");
            return this;
        }

        /**
         * Writes the entry.
         *
         * <p>Security events commit independently so they survive a rollback; record
         * changes join the caller's transaction so the two stand or fall together.
         *
         * <p>A failure to write the audit row is logged at ERROR but never propagated.
         * That is a deliberate trade-off: refusing a parishioner's login because the
         * audit table is unwritable would turn an auditing problem into an outage. The
         * ERROR lands in {@code logs/church-app-error.log}, so the gap is visible.
         */
        public void save() {
            try {
                if (entry.getEventType().isSecurityEvent()) {
                    auditWriter.writeIndependently(entry);
                } else {
                    auditWriter.writeWithCurrentTransaction(entry);
                }
            } catch (Exception ex) {
                log.error("AUDIT WRITE FAILED for event {} on {}/{} by {} -- the action itself "
                                + "was not affected, but this event is missing from the audit trail",
                        entry.getEventType(), entry.getEntityType(), entry.getEntityId(),
                        entry.getActorName(), ex);
            }
        }

        private void populateFromCurrentRequest() {
            HttpServletRequest request = currentRequest();
            if (request == null) {
                return;
            }
            entry.setIpAddress(clientIpOf(request));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
            if (entry.getCorrelationId() == null) {
                Object correlationId = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
                entry.setCorrelationId(correlationId != null ? correlationId.toString() : null);
            }
        }
    }

    private static HttpServletRequest currentRequest() {
        // Null outside a web request -- scheduled jobs and startup tasks audit fine without one.
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * Honours X-Forwarded-For so the real client address is recorded when the app sits
     * behind a proxy. The header is caller-supplied and therefore spoofable; it is
     * recorded as evidence, not trusted for access decisions.
     */
    private static String clientIpOf(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return truncate(first.trim(), 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
