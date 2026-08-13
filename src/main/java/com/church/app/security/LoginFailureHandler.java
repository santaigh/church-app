package com.church.app.security;

import com.church.app.entity.AuditEventType;
import com.church.app.service.AuditService;
import com.church.app.service.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * Runs after a failed sign-in: counts the attempt, locks the account at the threshold,
 * and decides which message the user sees.
 *
 * <p>Two outcomes are distinguished, by project decision:
 *
 * <ul>
 *   <li><b>Locked</b> -- the user is told, and pointed at the parish office. This is a
 *       deliberate trade-off: it also confirms to an attacker that they have succeeded in
 *       locking someone out, but the alternative leaves a genuine parishioner retrying
 *       forever with no explanation.</li>
 *   <li><b>Everything else</b> -- one generic message for wrong password, unknown account
 *       and disabled account alike, so the form cannot be used to discover which email
 *       addresses and mobile numbers belong to real parishioners.</li>
 * </ul>
 */
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final String loginPage;
    private final boolean platformScope;

    public LoginFailureHandler(AuditService auditService,
                               LoginAttemptService loginAttemptService,
                               String loginPage,
                               boolean platformScope) {
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
        this.loginPage = loginPage;
        this.platformScope = platformScope;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String attempted = request.getParameter("username");
        boolean showLockedMessage = exception instanceof LockedException;

        // Only a wrong password counts towards the lockout. An unknown account has
        // nothing to count against, and an already-locked one must not keep climbing.
        if (exception instanceof BadCredentialsException && StringUtils.hasText(attempted)) {
            boolean justLocked = loginAttemptService.registerFailure(attempted, platformScope);
            if (justLocked) {
                // Tell them on the attempt that locked it, rather than leaving them to
                // discover it on the next one.
                showLockedMessage = true;
            }
        }

        log.warn("Sign-in failed for '{}': {}", attempted, exception.getClass().getSimpleName());

        auditService.event(AuditEventType.LOGIN_FAILURE)
                .actorAnonymous(attempted)
                .describe(exception.getClass().getSimpleName())
                .failed()
                .save();

        setDefaultFailureUrl(loginPage + (showLockedMessage ? "?locked" : "?error"));
        super.onAuthenticationFailure(request, response, exception);
    }
}
