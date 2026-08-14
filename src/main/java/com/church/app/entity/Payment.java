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
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One receipt -- a single occasion on which a family handed over money.
 *
 * <p>Which months it settles is not stored here: that lives in
 * {@code payment_allocation}, because one receipt can cover several months and one
 * month can be settled by several receipts.
 *
 * <p>{@code amount} is what was handed over; {@code allocatedAmount} is how much of it
 * has been applied to dues. The difference is advance credit awaiting future months.
 */
@Entity
@Table(name = "payment")
@Filter(name = TenantFilters.TENANT_FILTER, condition = TenantFilters.CHURCH_CONDITION)
@Getter
@Setter
public class Payment extends AuditableEntity {

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

    /** Unique within a parish and year, e.g. {@code R-2026-0042}. */
    @Column(name = "receipt_no", nullable = false, length = 30)
    private String receiptNo;

    @Column(name = "receipt_year", nullable = false)
    private Short receiptYear;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "allocated_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 20)
    private PaymentMode paymentMode;

    @Column(name = "reference_no", length = 50)
    private String referenceNo;

    /** The member who took the money -- typically the secretary or an Anbiyam animator. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_member_id")
    private Member receivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.ACTIVE;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_date")
    private LocalDateTime voidedDate;

    @Column(name = "voided_user", length = 100)
    private String voidedUser;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "record_status", nullable = false, length = 20)
    private String recordStatus = "ACTIVE";

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Money received but not yet applied to any month -- advance credit. */
    public BigDecimal getUnallocatedAmount() {
        return amount.subtract(allocatedAmount);
    }

    public boolean hasUnallocatedAmount() {
        return getUnallocatedAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isVoided() {
        return status == PaymentStatus.VOID;
    }
}
