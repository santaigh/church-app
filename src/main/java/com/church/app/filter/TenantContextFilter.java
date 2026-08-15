package com.church.app.filter;

import com.church.app.security.AppUserPrincipal;
import com.church.app.security.SelectedChurch;
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
import java.util.Optional;

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

    private final SelectedChurch selectedChurch;

    public TenantContextFilter(SelectedChurch selectedChurch) {
        this.selectedChurch = selectedChurch;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            applyScope(request);
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled. Without this, the next request handled by this thread
            // would inherit the previous user's parish.
            TenantContext.clear();
        }
    }

    private void applyScope(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            // Anonymous request -- login page, static asset. Nothing tenant-scoped should
            // be read, and no scope is set.
            return;
        }

        if (principal.isPlatformUser()) {
            // A platform user who has entered a parish is scoped to it exactly as a
            // parish user would be. Without this the branding would say one church while
            // the queries still returned all of them -- the worst of both.
            Optional<SelectedChurch.Selection> selection = selectedChurch.from(request);
            if (selection.isPresent()) {
                TenantContext.setChurch(selection.get().churchId());
                log.debug("Tenant scope: church {} for platform user '{}'",
                        selection.get().churchId(), principal.getUsername());
                return;
            }
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
