package com.church.app.security;

import java.util.Optional;

/**
 * The parish whose data the current request may touch.
 *
 * <p>Held in a {@link ThreadLocal} because tenant scoping has to reach code that never
 * receives the principal as an argument -- repositories, and the Hibernate filter beneath
 * them. Passing a church id through every method signature would be the alternative, and
 * would be forgotten somewhere.
 *
 * <p>Two distinct states, and the difference matters:
 *
 * <ul>
 *   <li><b>A church id is set</b> -- an App-level user. Every query is restricted to it.</li>
 *   <li><b>Platform scope</b> -- a SaaS-level user, who legitimately reaches every parish.
 *       Filtering is switched off deliberately, which is why it is an explicit state
 *       rather than "no id set".</li>
 * </ul>
 *
 * <p>The unset state means neither: no authenticated user yet, or a background thread.
 * Nothing tenant-scoped should be read in that state, and the filter stays off.
 */
public final class TenantContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** What the current thread is allowed to see. */
    public record Scope(Long churchId, boolean platformWide) {

        static Scope forChurch(Long churchId) {
            return new Scope(churchId, false);
        }

        static Scope platform() {
            return new Scope(null, true);
        }
    }

    /** Restricts this thread to one parish. */
    public static void setChurch(Long churchId) {
        CURRENT.set(Scope.forChurch(churchId));
    }

    /** Marks this thread as platform-wide: filtering is intentionally not applied. */
    public static void setPlatformWide() {
        CURRENT.set(Scope.platform());
    }

    /**
     * The church to filter by, or empty when no filtering should be applied -- either
     * because the caller is platform-wide, or because no scope has been established.
     */
    public static Optional<Long> currentChurchId() {
        Scope scope = CURRENT.get();
        return scope == null || scope.platformWide()
                ? Optional.empty()
                : Optional.ofNullable(scope.churchId());
    }

    public static boolean isPlatformWide() {
        Scope scope = CURRENT.get();
        return scope != null && scope.platformWide();
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    /**
     * Clears the scope. Must be called in a finally block: threads are pooled and reused,
     * so a leftover value would silently hand one parish's scope to the next request.
     */
    public static void clear() {
        CURRENT.remove();
    }
}
