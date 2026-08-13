package com.church.app.entity;

/** How the money arrived. Mirrored by a CHECK constraint on {@code payment.payment_mode}. */
public enum PaymentMode {

    CASH("Cash"),
    UPI("UPI"),
    CHEQUE("Cheque"),
    BANK_TRANSFER("Bank Transfer"),
    CARD("Card"),
    OTHER("Other");

    private final String label;

    PaymentMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Modes that carry a cheque number or transaction reference worth recording. */
    public boolean expectsReference() {
        return this == CHEQUE || this == UPI || this == BANK_TRANSFER || this == CARD;
    }
}
