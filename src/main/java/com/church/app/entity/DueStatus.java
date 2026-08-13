package com.church.app.entity;

/**
 * Settlement state of one month's contribution.
 *
 * <p>Derived from the amounts rather than set by hand -- see
 * {@link PaymentDue#recalculateStatus()} -- so it can never disagree with them.
 *
 * <p>There is deliberately no {@code WAIVED}: this parish does not excuse dues.
 * A reduced contribution is handled by lowering the family's monthly amount.
 */
public enum DueStatus {

    /** Nothing received yet. */
    PENDING("Pending"),

    /** Something received, but less than the amount due. */
    PARTIAL("Partially Paid"),

    /** Settled in full. */
    PAID("Paid");

    private final String label;

    DueStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isOutstanding() {
        return this != PAID;
    }
}
