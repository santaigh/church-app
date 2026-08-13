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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A parishioner, and — for those with an email or mobile — a login account.
 *
 * <p>Every member carries a role and a password, so the App-level roles ({@code AppSA},
 * {@code AppAdmin}, {@code AppUser}) all live here. Platform roles cannot: {@code church_id},
 * {@code family_id} and {@code anbiyam_id} are NOT NULL, so they live in {@code saas_user}.
 *
 * <p>A member with neither email nor mobile still has a password but no way to identify
 * themselves at the login form, so they simply cannot sign in. That is intended.
 */
@Entity
@Table(name = "member")
@Getter
@Setter
public class Member extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "church_id", nullable = false)
    private Church church;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anbiyam_id", nullable = false)
    private Anbiyam anbiyam;

    /** Eager: needed to build authorities on every authentication. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /** BCrypt hash. The default password is hashed too -- nothing is stored in the clear. */
    @Column(name = "member_password", nullable = false, length = 100)
    private String memberPassword;

    /**
     * Stored as 0 or 1 in {@code password_flag}: 0 = still on the system default
     * password, 1 = the member has set their own.
     *
     * <p>Held as a boolean because MySQL reports {@code TINYINT(1)} as {@code BIT},
     * matching how {@code deleted_flag} and the other flag columns are mapped. The
     * values written to the database are unchanged.
     */
    @Column(name = "password_flag", nullable = false)
    private boolean passwordFlag;

    @Column(name = "gender", nullable = false, length = 10)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Login identifier. Unique where present; NULL for members who cannot sign in. */
    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "alternate_mobile", length = 20)
    private String alternateMobile;

    /** Login identifier. Unique where present; NULL for members who cannot sign in. */
    @Column(name = "email", length = 150)
    private String email;

    /** Column is spelled "catagory" in the schema; kept as-is to avoid a rename migration. */
    @Column(name = "catagory", length = 100)
    private String category;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    /** Non-null means locked. There is no timed expiry -- an administrator must clear it. */
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

    /** True while the member still has to replace the system-assigned password (flag = 0). */
    public boolean isUsingDefaultPassword() {
        return !passwordFlag;
    }

    /** Locked accounts stay locked until an administrator clears {@code lockedAt}. */
    public boolean isLocked() {
        return lockedAt != null;
    }

    /** A member with no email and no mobile has no way to identify themselves at login. */
    public boolean canSignIn() {
        return (email != null && !email.isBlank()) || (mobile != null && !mobile.isBlank());
    }

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder(firstName);
        if (lastName != null && !lastName.isBlank()) {
            sb.append(' ').append(lastName);
        }
        return sb.toString();
    }
}
