package com.church.app.service;

import com.church.app.entity.ActorType;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.AuditLog;
import com.church.app.entity.Operation;
import com.church.app.entity.Resource;
import com.church.app.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Not {@code @Transactional}: security events commit in their own transaction, so a
 * rolled-back test would not see them. Rows written here are cleaned up explicitly.
 */
@SpringBootTest
class AuditServiceTests {

    private static final String TEST_ENTITY = "test-entity";

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void removeTestRows() {
        List<AuditLog> written = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByEventTimeDesc(TEST_ENTITY, 999L);
        auditLogRepository.deleteAll(written);
    }

    @Test
    @DisplayName("a security event is written even with no surrounding transaction")
    void securityEventIsWritten() {
        auditService.event(AuditEventType.LOGIN_FAILURE)
                .actorAnonymous("someone@example.com")
                .on(TEST_ENTITY, 999L, "Login attempt")
                .describe("Bad password")
                .failed()
                .save();

        List<AuditLog> rows = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByEventTimeDesc(TEST_ENTITY, 999L);
        assertEquals(1, rows.size());

        AuditLog row = rows.get(0);
        assertEquals(AuditEventType.LOGIN_FAILURE, row.getEventType());
        assertEquals(ActorType.ANONYMOUS, row.getActorType());
        assertEquals("someone@example.com", row.getActorName());
        assertTrue(row.isFailure());
        assertNotNull(row.getUuid());
        assertNotNull(row.getEventTime());
        // No church: a failed login cannot be attributed to a parish yet.
        assertNull(row.getChurchId());
    }

    @Test
    @DisplayName("before and after values are captured")
    void recordsOldAndNewValues() {
        auditService.event(AuditEventType.RECORD_UPDATED)
                .actorMember(2L, "Mary Arulraj", 1L)
                .on(TEST_ENTITY, 999L, "Mary Arulraj")
                .permission(Resource.MEMBER, Operation.EDIT)
                .changed("+919840100002", "+919840155555")
                .describe("Mobile number updated")
                .save();

        AuditLog row = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByEventTimeDesc(TEST_ENTITY, 999L).get(0);
        assertEquals("+919840100002", row.getOldValue());
        assertEquals("+919840155555", row.getNewValue());
        assertEquals(ActorType.MEMBER, row.getActorType());
        assertEquals(1L, row.getChurchId());
        assertEquals(Resource.MEMBER, row.getResource());
        assertEquals(Operation.EDIT, row.getOperation());
        assertFalse(row.isFailure());
    }

    @Test
    @DisplayName("platform actions are recorded with no church")
    void saasActionsHaveNoChurch() {
        auditService.event(AuditEventType.DATA_EXPORTED)
                .actorSaasUser(1L, "Platform Super Admin")
                .on(TEST_ENTITY, 999L, "Member export")
                .permission(Resource.MEMBER, Operation.EXPORT)
                .save();

        AuditLog row = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByEventTimeDesc(TEST_ENTITY, 999L).get(0);
        assertEquals(ActorType.SAAS_USER, row.getActorType());
        assertNull(row.getChurchId());
        assertEquals(AuditEventType.DATA_EXPORTED, row.getEventType());
    }

    @Test
    @DisplayName("over-long values are truncated rather than failing the write")
    void longValuesAreTruncated() {
        String tooLong = "x".repeat(9000);
        auditService.event(AuditEventType.RECORD_UPDATED)
                .actorMember(2L, "Mary Arulraj", 1L)
                .on(TEST_ENTITY, 999L, "y".repeat(600))
                .changed(tooLong, tooLong)
                .describe("z".repeat(900))
                .save();

        AuditLog row = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByEventTimeDesc(TEST_ENTITY, 999L).get(0);
        assertEquals(4000, row.getOldValue().length());
        assertEquals(255, row.getEntityLabel().length());
        assertEquals(500, row.getDescription().length());
    }

    @Test
    @DisplayName("security and record events are classified correctly")
    void eventClassification() {
        assertTrue(AuditEventType.LOGIN_FAILURE.isSecurityEvent());
        assertTrue(AuditEventType.PERMISSION_REVOKED.isSecurityEvent());
        assertTrue(AuditEventType.ROLE_ASSIGNED.isSecurityEvent());
        assertFalse(AuditEventType.RECORD_UPDATED.isSecurityEvent());
        assertFalse(AuditEventType.DATA_EXPORTED.isSecurityEvent());
    }
}
