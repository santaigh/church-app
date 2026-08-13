package com.church.app.security;

import com.church.app.entity.AuditEventType;
import com.church.app.service.AuditService;
import com.church.app.service.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Runs after a successful sign-in: records the event and decides where the user lands.
 *
 * <p>One instance per filter chain, each with its own landing page -- parish users to
 * their church dashboard, platform users to the platform view.
 */
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final String landingPage;
    private final boolean platformScope;

    public LoginSuccessHandler(AuditService auditService,
                               LoginAttemptService loginAttemptService,
                               String landingPage,
                               boolean platformScope) {
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
        this.landingPage = landingPage;
        this.platformScope = platformScope;
        setDefaultTargetUrl(landingPage);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            // Clears the failure counter and stamps last_login_at.
            loginAttemptService.registerSuccess(principal.getUsername(), platformScope);
            recordAudit(principal);
            log.info("Sign-in succeeded for {} '{}' (role {}, church {})",
                    principal.getActorType(), principal.getUsername(), principal.getRoleCode(),
                    principal.getChurchId() != null ? principal.getChurchId() : "platform-wide");
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private void recordAudit(AppUserPrincipal principal) {
        var entry = auditService.event(AuditEventType.LOGIN_SUCCESS);
        if (principal.isPlatformUser()) {
            entry.actorSaasUser(principal.getUserId(), principal.getDisplayName());
        } else {
            entry.actorMember(principal.getUserId(), principal.getDisplayName(), principal.getChurchId());
        }
        entry.describe("Signed in as " + principal.getRoleCode()).save();
    }

    /** Where this chain sends users once authenticated. */
    public String getLandingPage() {
        return landingPage;
    }
}
