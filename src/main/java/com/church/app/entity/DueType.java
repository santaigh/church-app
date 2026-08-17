package com.church.app.entity;

/**
 * What kind of thing a {@link PaymentDue} row is.
 *
 * <p>The distinction exists so a summary can separate money a family has fallen behind on
 * this year from money carried in when the parish started using the application. Folded
 * together they tell a priest very little; kept apart they answer two different questions.
 */
public enum DueType {

    /** One month's contribution, generated from the family's monthly amount. */
    MONTHLY("Monthly"),

    /**
     * Everything owed before the parish began using this application, as one line.
     *
     * <p>Dated the month before the family's {@code dues_start_date}, so it can never
     * collide with a generated month and always sorts first when money is allocated.
     */
    OPENING_BALANCE("Opening balance");

    private final String label;

    DueType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
