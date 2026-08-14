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
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * How much of one payment was applied to one month's due.
 *
 * <p>This is the piece that makes the two awkward cases representable:
 * a ₹500 receipt clearing January, February and part of March is three rows here;
 * a March due settled by two separate receipts is two rows here.
 *
 * <p>Voiding a receipt deletes its allocation rows and restores the dues, keeping the
 * invariant that a due's {@code amountPaid} always equals the sum of its allocations.
 */
@Entity
@Table(name = "payment_allocation")
@Filter(name = TenantFilters.TENANT_FILTER, condition = TenantFilters.CHURCH_CONDITION)
@Getter
@Setter
public class PaymentAllocation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "church_id", nullable = false)
    private Church church;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_due_id", nullable = false)
    private PaymentDue paymentDue;

    @Column(name = "allocated_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedAmount;
}
