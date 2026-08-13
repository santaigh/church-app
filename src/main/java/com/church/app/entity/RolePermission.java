package com.church.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * One granted permission: this role may perform this operation on this resource.
 *
 * <p>The presence of the row IS the grant -- there is no {@code allowed} flag and no
 * soft delete. Revoking removes the row.
 *
 * @see Operation
 * @see Resource
 */
@Entity
@Table(name = "role_permission")
@Getter
@Setter
public class RolePermission extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_code", nullable = false, length = 50)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_code", nullable = false, length = 30)
    private Operation operation;

    @Column(name = "record_status", nullable = false, length = 20)
    private String recordStatus = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * The authority string Spring Security checks against, e.g. {@code PERM_MEMBER_EDIT}.
     * Used by {@code @PreAuthorize} and by the templates when deciding whether to render
     * an action.
     */
    public String toAuthority() {
        return "PERM_" + resource.name() + "_" + operation.name();
    }
}
