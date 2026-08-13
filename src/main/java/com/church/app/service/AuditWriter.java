package com.church.app.service;

import com.church.app.entity.AuditLog;
import com.church.app.repository.AuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists audit rows. Separate from {@link AuditService} because the two propagation
 * modes have to go through the Spring proxy -- calling them from within the same bean
 * would bypass it and silently ignore the {@code @Transactional} settings.
 */
@Component
class AuditWriter {

    private final AuditLogRepository auditLogRepository;

    AuditWriter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Commits in its own transaction, independent of the caller's.
     *
     * <p>Used for security events: a failed login rolls back nothing useful, but the
     * record that it happened must survive regardless. If this joined the caller's
     * transaction, the evidence would roll back with it -- which is precisely backwards
     * for the events an attacker would most like erased.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void writeIndependently(AuditLog entry) {
        auditLogRepository.save(entry);
    }

    /**
     * Joins the caller's transaction.
     *
     * <p>Used for record changes, where the audit row and the change it describes must
     * commit or roll back together -- an audit entry for an update that never happened
     * would be a lie.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    void writeWithCurrentTransaction(AuditLog entry) {
        auditLogRepository.save(entry);
    }
}
