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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A household -- the parish family register entry.
 *
 * <p>Deliberately thin. Address lives on {@code member_ext} per person, so it is not
 * duplicated here; what remains is genuinely family-level: the register code, the
 * Anbiyam the household belongs to, its head, and a shared phone number.
 *
 * <p>{@code familyCode} is unique per church, not globally -- each parish numbers its
 * own register from 1.
 */
@Entity
@Table(name = "family")
@Getter
@Setter
public class Family extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "church_id", nullable = false)
    private Church church;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anbiyam_id", nullable = false)
    private Anbiyam anbiyam;

    @Column(name = "family_code", nullable = false, length = 30)
    private String familyCode;

    @Column(name = "family_name", nullable = false, length = 150)
    private String familyName;

    /** Head of the household. Plain id: member.family_id is NOT NULL, so an FK both ways would be circular. */
    @Column(name = "head_member_id")
    private Long headMemberId;

    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * The family's agreed monthly contribution. Varies by family, and the priest may
     * raise or lower it.
     *
     * <p>Changing this affects dues raised from now on only -- {@code payment_due}
     * snapshots the rate at the moment each month is raised, so past arrears are never
     * retrospectively recalculated.
     */
    @Column(name = "monthly_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyAmount = BigDecimal.ZERO;

    /** No dues are raised for months before this date, so new families start clean. */
    @Column(name = "dues_start_date")
    private LocalDate duesStartDate;

    @Column(name = "record_status", nullable = false, length = 20)
    private String recordStatus = "ACTIVE";

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
