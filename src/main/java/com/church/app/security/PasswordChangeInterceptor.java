package com.church.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Holds a user on the change-password screen until they have replaced the
 * system-assigned password.
 *
 * <p>Redirecting once at login is not enough on its own -- a user could simply type
 * another URL and carry on. Checking every request is what makes
 * {@code password_flag = 0} mean the account genuinely cannot be used for anything else.
 */
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    /**
     * Reachable while the flag is still 0. Without these the user would be trapped:
     * unable to reach the change form, and unable to sign out.
     */
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/change-password", "/saas/change-password",
            "/forgot-password", "/saas/forgot-password",
            "/login", "/saas/login",
            "/logout", "/saas/logout",
            "/error", "/access-denied"
    );

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/css/", "/js/", "/images/", "/webjars/"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }
        if (!(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return true;
        }
        if (!principal.isUsingDefaultPassword()) {
            return true;
        }

        String uri = request.getRequestURI();
        if (ALLOWED_PATHS.contains(uri) || ALLOWED_PREFIXES.stream().anyMatch(uri::startsWith)) {
            return true;
        }

        // Each chain has its own change-password page.
        String target = principal.isPlatformUser() ? "/saas/change-password" : "/change-password";
        response.sendRedirect(request.getContextPath() + target);
        return false;
    }
}
