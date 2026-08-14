package com.church.app.config;

import com.church.app.entity.TenantFilters;
import com.church.app.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Switches the Hibernate tenant filter on for the current session, using the church
 * established by {@code TenantContextFilter}.
 *
 * <p>Ordering is the subtle part. A Hibernate filter lives on a {@code Session}, so this
 * advice has to run <em>inside</em> the transaction, after the session exists. Spring's
 * transaction advisor normally runs at lowest precedence, which would put this outside it
 * -- the filter would be enabled on a throwaway session and quietly do nothing.
 * {@link TransactionOrderConfig} moves the transaction advisor to a high precedence so
 * this can sit inside it.
 *
 * <p>When no church is in scope the filter is simply not enabled: an anonymous request,
 * or a platform user who is entitled to see every parish.
 */
@Aspect
@Component
@Order(TenantFilterAspect.ORDER)
public class TenantFilterAspect {

    /** Must be a lower precedence (higher number) than the transaction advisor. */
    public static final int ORDER = 100;

    private static final Logger log = LoggerFactory.getLogger(TenantFilterAspect.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.church.app.repository..*(..))")
    public void enableTenantFilter() {
        TenantContext.currentChurchId().ifPresent(churchId -> {
            Session session = entityManager.unwrap(Session.class);
            // Re-enabling with the same parameter is harmless and keeps this cheap.
            session.enableFilter(TenantFilters.TENANT_FILTER)
                    .setParameter(TenantFilters.CHURCH_ID_PARAM, churchId);
            log.trace("Tenant filter enabled for church {}", churchId);
        });
    }
}
