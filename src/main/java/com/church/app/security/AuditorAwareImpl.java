package com.church.app.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the name written into {@code created_user} and {@code updated_user}.
 *
 * <p>Those columns existed from the first migration and were never populated -- every
 * row in the database says who it belongs to but not who put it there. This closes that.
 *
 * <p>The sign-in identifier is used rather than the display name: two parishioners may
 * share a name, and the point of the column is to identify one account.
 *
 * <p>Falls back to {@code SYSTEM} for work with no signed-in user behind it -- migrations
 * and seed data already use that value.
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {

    static final String SYSTEM = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        return Optional.of(principal == null ? SYSTEM : principal.getUsername());
    }
}
