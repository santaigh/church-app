package com.church.app.entity;

/** Names shared between the filter definition, the entity annotations and the aspect. */
public final class TenantFilters {

    /** Hibernate filter restricting a query to one church. */
    public static final String TENANT_FILTER = "tenantFilter";

    /** Parameter carrying the church id. */
    public static final String CHURCH_ID_PARAM = "churchId";

    /** The SQL fragment applied to every tenant-scoped entity. */
    public static final String CHURCH_CONDITION = "church_id = :churchId";

    private TenantFilters() {
    }
}
