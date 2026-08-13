package com.church.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * One of the six roles. Stored as a row rather than a Java enum so roles can become
 * runtime-editable without a schema change.
 *
 * <p>{@code roleCode} is the value security checks compare against, so its casing is
 * part of the contract: {@code SaaSSAdmin}, {@code SaaSAdmin}, {@code SaaSUser},
 * {@code AppSA}, {@code AppAdmin}, {@code AppUser}.
 */
@Entity
@Table(name = "role")
@Getter
@Setter
public class Role extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    @Column(name = "role_code", nullable = false, length = 30)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_level", nullable = false, length = 10)
    private RoleLevel roleLevel;

    @Column(name = "description")
    private String description;

    /** Shipped with the product; the UI must refuse to delete these. */
    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined = true;

    @Column(name = "record_status", nullable = false, length = 20)
    private String recordStatus = "ACTIVE";

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** True for the three platform roles, which live in {@code saas_user}. */
    public boolean isPlatformRole() {
        return roleLevel == RoleLevel.SAAS;
    }
}
