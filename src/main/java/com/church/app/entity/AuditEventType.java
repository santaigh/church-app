package com.church.app.entity;

/**
 * The kinds of event worth keeping permanently.
 *
 * <p>Kept deliberately coarse. The point of an audit trail is that a parish priest or
 * committee can read it, so one entry should correspond to one thing a person did --
 * not to every column Hibernate happened to flush.
 */
public enum AuditEventType {

    // ---- authentication ---------------------------------------------------
    LOGIN_SUCCESS("Signed in"),
    LOGIN_FAILURE("Failed sign-in attempt"),
    LOGOUT("Signed out"),
    ACCOUNT_LOCKED("Account locked after repeated failures"),
    ACCOUNT_UNLOCKED("Account unlocked by an administrator"),
    PASSWORD_CHANGED("Password changed by the user"),
    PASSWORD_RESET("Password reset to the system default"),

    // ---- records ----------------------------------------------------------
    RECORD_CREATED("Record created"),
    RECORD_UPDATED("Record updated"),
    RECORD_DELETED("Record deleted"),
    RECORD_ACTIVATED("Record activated"),
    RECORD_DEACTIVATED("Record deactivated"),

    // ---- data leaving the system -----------------------------------------
    /** Worth its own type: an export removes personal data from the application's control. */
    DATA_EXPORTED("Data exported"),

    // ---- privilege --------------------------------------------------------
    /** The most security-sensitive change in the system. */
    ROLE_ASSIGNED("Role assigned to a user"),
    PERMISSION_GRANTED("Permission granted to a role"),
    PERMISSION_REVOKED("Permission revoked from a role"),

    // ---- platform staff working inside a parish ---------------------------
    /**
     * A platform account narrowed itself to one parish. Worth recording: from here its
     * reads and writes are scoped to that church, and the trail should say which.
     */
    CHURCH_ENTERED("Platform user entered a parish"),
    CHURCH_EXITED("Platform user left a parish"),

    // ---- access denied ----------------------------------------------------
    ACCESS_DENIED("Access denied");

    private final String label;

    AuditEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Authentication and privilege events are recorded even when the surrounding work fails. */
    public boolean isSecurityEvent() {
        return switch (this) {
            case LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED,
                 PASSWORD_CHANGED, PASSWORD_RESET, ROLE_ASSIGNED, PERMISSION_GRANTED,
                 PERMISSION_REVOKED, ACCESS_DENIED -> true;
            default -> false;
        };
    }
}
