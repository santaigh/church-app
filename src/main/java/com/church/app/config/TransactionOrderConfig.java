package com.church.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Moves Spring's transaction advisor to a high precedence.
 *
 * <p>Sole purpose: let {@link TenantFilterAspect} run <em>inside</em> the transaction.
 * A Hibernate filter is enabled on a {@code Session}, so the advice that enables it must
 * execute after the transaction has begun and the session exists. With the default
 * ordering the transaction advisor sits at lowest precedence, nothing can be placed
 * inside it, and the filter would be applied to a session that is immediately discarded.
 *
 * <p>The failure mode that would cause is the dangerous kind: no error, no warning,
 * queries simply unfiltered. Hence the explicit ordering rather than relying on defaults.
 */
@Configuration
@EnableTransactionManagement(order = TransactionOrderConfig.TRANSACTION_ADVISOR_ORDER)
public class TransactionOrderConfig {

    /** Any value below {@link TenantFilterAspect#ORDER} places transactions outermost. */
    public static final int TRANSACTION_ADVISOR_ORDER = 0;
}
