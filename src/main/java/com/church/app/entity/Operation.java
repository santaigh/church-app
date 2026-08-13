package com.church.app.entity;

/**
 * What a role may do to a {@link Resource}.
 *
 * <p>Held in code rather than a table because a new operation can only exist once a
 * developer has written something that performs it -- no parish administrator will ever
 * invent a seventh. The role-to-permission *mapping* is data; this vocabulary is not.
 *
 * <p>Mirrored by a {@code CHECK} constraint on {@code role_permission.operation_code},
 * so adding a constant here also needs a one-line migration widening that constraint.
 */
public enum Operation {

    ADD("Add"),
    VIEW("View"),
    EDIT("Edit"),
    DELETE("Delete"),
    ACTIVATE_DEACTIVATE("Activate/Deactivate"),
    EXPORT("Export");

    private final String label;

    Operation(String label) {
        this.label = label;
    }

    /** Human-readable form, for permission screens. */
    public String getLabel() {
        return label;
    }
}
