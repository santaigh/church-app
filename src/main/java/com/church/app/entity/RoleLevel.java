package com.church.app.entity;

/**
 * Whether a role acts across the whole platform or inside a single church.
 *
 * <p>This decides which table the account lives in: {@link #SAAS} roles are held in
 * {@code saas_user}, which has no {@code church_id}; {@link #APP} roles are held in
 * {@code member}, where {@code church_id} is NOT NULL.
 */
public enum RoleLevel {

    /** Platform-wide -- reaches every church. */
    SAAS,

    /** Scoped to the one church the member belongs to. */
    APP
}
