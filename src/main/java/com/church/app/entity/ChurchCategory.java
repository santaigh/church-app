package com.church.app.entity;

/**
 * What kind of church a record represents.
 *
 * <p>A {@link #SUBSTATION} is an outstation attached to a parent church, which is what
 * {@code church.parent_church_id} points at; a {@link #STATION} stands on its own.
 *
 * <p>Kept in code rather than a lookup table because the list is short and stable. The
 * trade-off accepted with that choice: {@code church.category_id} stays an unvalidated
 * {@code varchar}, so a bad value can only be caught by the application, not the database.
 */
public enum ChurchCategory {

    STATION("Station"),
    SUBSTATION("Substation");

    private final String label;

    ChurchCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
