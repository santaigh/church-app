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
import java.time.Month;

/**
 * What one family owes for one month.
 *
 * <p>The existence of this row is what makes arrears answerable: a family that paid
 * nothing has no {@code payment} row, so without a record of the expectation there
 * would be nothing to report as pending.
 *
 * <p>{@code amountDue} is a snapshot of the family's rate when the due was raised, not
 * a live reference to it. Changing a family's monthly amount therefore affects future
 * months only, and never rewrites what was historically owed.
 */
@Entity
@Table(name = "payment_due")
@Filter(name = TenantFilters.TENANT_FILTER, condition = TenantFilters.CHURCH_CONDITION)
@Getter
@Setter
public class PaymentDue extends AuditableEntity {

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

    @Column(name = "due_year", nullable = false)
    private Short dueYear;

    /** 1-12. */
    @Column(name = "due_month", nullable = false)
    private Byte dueMonth;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountDue = BigDecimal.ZERO;

    /** Always equals the sum of this due's allocations. */
    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DueStatus status = DueStatus.PENDING;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "record_status", nullable = false, length = 20)
    private String recordStatus = "ACTIVE";

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** What is still owed for this month. Never negative. */
    public BigDecimal getBalance() {
        BigDecimal balance = amountDue.subtract(amountPaid);
        return balance.signum() < 0 ? BigDecimal.ZERO : balance;
    }

    /**
     * Derives {@link #status} from the amounts. Call after changing {@code amountPaid}
     * so the two can never disagree.
     */
    public void recalculateStatus() {
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            status = DueStatus.PENDING;
        } else if (amountPaid.compareTo(amountDue) >= 0) {
            status = DueStatus.PAID;
        } else {
            status = DueStatus.PARTIAL;
        }
    }

    public boolean isOutstanding() {
        return status.isOutstanding();
    }

    /** e.g. "March 2026", for receipts and arrears listings. */
    public String getPeriodLabel() {
        return Month.of(dueMonth).getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + dueYear;
    }
}
