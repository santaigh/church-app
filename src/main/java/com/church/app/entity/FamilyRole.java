package com.church.app.entity;

/**
 * A member's position in their family -- not their job, and not a parish duty.
 *
 * <p>Declared in the order screens should list them: the head first, then the spouse,
 * the children, and finally a parent living in the household.
 *
 * <p>{@link #HEAD} covers both Kudumba Thalaivan and Thalaivi -- {@code gender}
 * distinguishes them, and likewise turns a {@link #CHILD} into a son or a daughter. That
 * is why no gendered values are needed here, and why a Tamil label can be derived from
 * the pair when the interface is translated.
 *
 * <p>{@link #FATHER} and {@link #MOTHER} are relative to the head: a parent of the head
 * or of the spouse who lives with the family, typically after a death or a move. Both
 * sets of parents may be present, which is why neither is limited to one.
 *
 * <p>In code rather than a table for the same reason as {@link Operation} and
 * {@link Resource}: the values are queried -- "who heads this family" is a real
 * question -- so they should be compile-checked.
 */
public enum FamilyRole {

    HEAD("Head of family"),
    SPOUSE("Spouse"),
    CHILD("Child"),
    FATHER("Father"),
    MOTHER("Mother");

    private final String label;

    FamilyRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
