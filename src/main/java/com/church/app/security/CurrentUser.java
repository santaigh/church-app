package com.church.app.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the signed-in principal from the security context.
 *
 * <p>Exists because the page header is rendered on every view, and threading
 * {@code @AuthenticationPrincipal} through every controller method that might one day
 * render a header is the duplication the layout fragment is meant to remove.
 *
 * <p>Returns null for anonymous requests -- Spring puts the string
 * {@code "anonymousUser"} in the principal slot rather than nothing at all, so the type
 * check below is doing real work.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AppUserPrincipal principalOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof AppUserPrincipal principal ? principal : null;
    }
}
