package com.church.app.filter;

import com.church.app.security.AppUserPrincipal;
import com.church.app.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes the tenant scope for the request from the signed-in principal.
 *
 * <p>Registered as a {@code @Component}, so Spring Boot places it in the servlet chain
 * after Spring Security's filters -- by which point the {@code SecurityContext} is
 * populated and the principal is available.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            applyScope();
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled. Without this, the next request handled by this thread
            // would inherit the previous user's parish.
            TenantContext.clear();
        }
    }

    private void applyScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            // Anonymous request -- login page, static asset. Nothing tenant-scoped should
            // be read, and no scope is set.
            return;
        }

        if (principal.isPlatformUser()) {
            TenantContext.setPlatformWide();
            log.debug("Tenant scope: platform-wide for '{}'", principal.getUsername());
            return;
        }

        Long churchId = principal.getChurchId();
        if (churchId == null) {
            // An App-level account with no church would otherwise read everything. Refuse
            // rather than fall through to an unfiltered state.
            log.error("App-level user '{}' has no church id; leaving tenant scope unset",
                    principal.getUsername());
            return;
        }

        TenantContext.setChurch(churchId);
        log.debug("Tenant scope: church {} for '{}'", churchId, principal.getUsername());
    }
}
