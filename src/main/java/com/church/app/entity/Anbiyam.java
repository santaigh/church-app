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
import lombok.Getter;
import lombok.Setter;

/**
 * A basic Christian community within a parish -- the neighbourhood grouping that
 * families belong to.
 *
 * <p>Note this table has no {@code version} column, unlike most others here, so there
 * is no {@code @Version} field.
 */
@Entity
@Table(name = "anbiyam")
@Getter
@Setter
public class Anbiyam extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "church_id", nullable = false)
    private Church church;

    /** Usually Tamil script -- the column and connection are utf8mb4 for this reason. */
    @Column(name = "anbiyam_name", nullable = false, length = 150)
    private String anbiyamName;

    /** The animator. Mapped as a plain id: pairing it with member.anbiyam_id would be circular. */
    @Column(name = "head_member_id")
    private Long headMemberId;

    @Column(name = "area_description")
    private String areaDescription;

    @Column(name = "record_status", nullable = false, length = 20)
    private String recordStatus = "ACTIVE";

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag;
}
