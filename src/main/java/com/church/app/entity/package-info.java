/**
 * JPA entities and domain enums.
 *
 * <p>The tenant filter is declared here, once, and applied by {@code @Filter} on each
 * church-scoped entity. Declaring it at package level keeps a single definition rather
 * than repeating it on whichever entity happens to be alphabetically first.
 *
 * <p>It is switched on per request by {@code TenantFilterAspect}, using the church id
 * established by {@code TenantContextFilter}. When no church is in scope -- an anonymous
 * request, or a platform user who legitimately sees every parish -- the filter is simply
 * not enabled.
 *
 * <p>Note what this does NOT cover: Hibernate filters are ignored on primary-key loads,
 * so {@code EntityManager.find()} and therefore {@code JpaRepository.findById()} bypass
 * it entirely. That gap is closed separately by {@code TenantAwareRepositoryImpl}, which
 * re-routes findById through a criteria query so the filter applies. Both pieces are
 * required; either alone leaves a hole.
 */
@FilterDef(
        name = TenantFilters.TENANT_FILTER,
        parameters = @ParamDef(name = TenantFilters.CHURCH_ID_PARAM, type = Long.class)
)
package com.church.app.entity;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
