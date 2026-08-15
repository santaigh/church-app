package com.church.app.entity;

/**
 * The three kinds of clergy assigned to a parish.
 *
 * <p>In code rather than a table for the same reason as {@link Operation} and
 * {@link Resource}: the list is short, stable, and queried by name -- "who is the parish
 * priest here" is a real query, and an enum makes it compile-checked.
 *
 * <p>The honorific stays part of the stored name ({@code Fr. Antony Raj},
 * {@code Br. Selvam}) rather than being derived from the role, because that is how a
 * parish writes it and how the existing records already read.
 */
public enum ClergyRole {

    /** Leads the parish: sacraments and administration. Exactly one serves at a time. */
    PARISH_PRIEST("Parish Priest"),

    /** Supports the parish priest. Optional, and there may be several. */
    ASSISTANT_PRIEST("Assistant Priest"),

    /** A final-year seminarian, not yet ordained. Optional, and there may be several. */
    BROTHER("Brother");

    private final String label;

    ClergyRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
