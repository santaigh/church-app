package com.church.app.security;

import com.church.app.entity.AuditEventType;
import com.church.app.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/** Records sign-outs, so a session's start and end both appear in the audit trail. */
@Component
public class AuditingLogoutHandler implements LogoutHandler {

    private final AuditService auditService;

    public AuditingLogoutHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // Null when the session had already expired -- nothing to attribute the event to.
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return;
        }

        var entry = auditService.event(AuditEventType.LOGOUT);
        if (principal.isPlatformUser()) {
            entry.actorSaasUser(principal.getUserId(), principal.getDisplayName());
        } else {
            entry.actorMember(principal.getUserId(), principal.getDisplayName(), principal.getChurchId());
        }
        entry.save();
    }
}
