package com.church.app.entity;

/**
 * Which kind of account performed an audited action.
 *
 * <p>Needed because {@code actor_id} alone is ambiguous: member 3 and saas_user 3 are
 * different people, and the two live in separate tables.
 */
public enum ActorType {

    /** A parish account, from the {@code member} table. */
    MEMBER,

    /** A platform account, from the {@code saas_user} table. */
    SAAS_USER,

    /** The application itself -- scheduled jobs, migrations, startup tasks. */
    SYSTEM,

    /** Nobody authenticated: a failed login is recorded before identity is established. */
    ANONYMOUS
}
