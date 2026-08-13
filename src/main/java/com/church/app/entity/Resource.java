package com.church.app.entity;

/**
 * A protected area of the application -- roughly one per module in the Phase 1 scope.
 *
 * <p>In code rather than a table for the same reason as {@link Operation}: a resource
 * exists only because a developer built that screen.
 *
 * <p>Unlike {@code Operation}, this is NOT mirrored by a database {@code CHECK}
 * constraint. The list grows as modules land through Phase 1, and requiring a migration
 * per screen would be friction for no real gain; {@code resource_code} is validated in
 * the service layer instead.
 */
public enum Resource {

    CHURCH("Church Management"),
    USER("User Management"),
    ROLE("Role & Permissions"),
    MEMBER("Member Management"),
    FAMILY("Family Details"),
    ANBIYAM("Anbiyam Management"),
    ANBIYAM_MAPPING("Anbiyam Mapping"),
    PAYMENT("Payments"),
    DASHBOARD("Dashboard"),
    REPORT("Reports & Export"),
    AUDIT("Audit");

    private final String label;

    Resource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
