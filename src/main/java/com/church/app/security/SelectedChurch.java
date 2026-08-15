package com.church.app.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * The parish a platform user is currently working inside.
 *
 * <p>A {@code saas_user} has no {@code church_id} -- that is what lets them reach every
 * parish. Choosing one narrows them to it for the rest of the session, so their reads
 * and writes behave exactly like a parish user's.
 *
 * <p>Held in the HTTP session rather than on {@link AppUserPrincipal}, which is immutable
 * and built once at sign-in; the choice has to be changeable without signing out again.
 *
 * <p>Only consulted for platform accounts. A parish user's church comes from their own
 * record, so a selection sitting in their session -- however it got there -- is ignored.
 */
@Component
public class SelectedChurch {

    private static final String KEY = SelectedChurch.class.getName() + ".selection";

    /** @param churchName carried alongside the id so the page header costs no query */
    public record Selection(Long churchId, String churchName) {
    }

    public Optional<Selection> from(HttpServletRequest request) {
        // getSession(false): reading must never create a session, or every anonymous
        // request for a static asset would allocate one.
        HttpSession session = request.getSession(false);
        return session == null
                ? Optional.empty()
                : Optional.ofNullable((Selection) session.getAttribute(KEY));
    }

    /** For callers with no request to hand -- a view advice, or the logo endpoint. */
    public Optional<Selection> current() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? from(attributes.getRequest())
                : Optional.empty();
    }

    public void select(HttpServletRequest request, Selection selection) {
        request.getSession(true).setAttribute(KEY, selection);
    }

    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(KEY);
        }
    }
}
