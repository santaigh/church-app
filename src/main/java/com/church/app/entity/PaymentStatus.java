package com.church.app.entity;

/**
 * Whether a receipt still stands.
 *
 * <p>A mistaken receipt is never deleted -- it is voided, which reverses its
 * allocations and restores the dues while leaving the original visible. A parish
 * cash book with rows silently removed is not a cash book.
 */
public enum PaymentStatus {

    ACTIVE("Active"),
    VOID("Voided");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
