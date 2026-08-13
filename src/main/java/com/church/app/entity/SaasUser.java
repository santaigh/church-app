package com.church.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A platform-level account: {@code SaaSSAdmin}, {@code SaaSAdmin} or {@code SaaSUser}.
 *
 * <p>Has no {@code church_id} by design -- these roles reach every church, so scoping
 * them to one would be wrong. That is precisely why they cannot be {@link Member} rows.
 *
 * <p>Mirrors the login columns on {@link Member} so both sign-in flows behave identically.
 */
@Entity
@Table(name = "saas_user")
@Getter
@Setter
public class SaasUser extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    /** Eager: needed to build authorities on every authentication. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "user_password", nullable = false, length = 100)
    private String userPassword;

    /**
     * Stored as 0 or 1 in {@code password_flag}: 0 = still on the system default
     * password, 1 = the user has set their own. Boolean for the same reason as
     * {@link Member#isUsingDefaultPassword()} -- MySQL reports {@code TINYINT(1)} as {@code BIT}.
     */
    @Column(name = "password_flag", nullable = false)
    private boolean passwordFlag;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    /** Non-null means locked. No timed expiry -- an administrator must clear it. */
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "record_status", nullable = false, length = 20)
    private String recordStatus = "ACTIVE";

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** True while the user still has to replace the system-assigned password (flag = 0). */
    public boolean isUsingDefaultPassword() {
        return !passwordFlag;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder(firstName);
        if (lastName != null && !lastName.isBlank()) {
            sb.append(' ').append(lastName);
        }
        return sb.toString();
    }
}
