package com.church.app.config;

import com.church.app.repository.support.TenantAwareRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Registers {@link TenantAwareRepositoryImpl} as the base class for every repository, so
 * the tenant-safe {@code findById} applies everywhere rather than being opted into.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.church.app.repository",
        repositoryBaseClass = TenantAwareRepositoryImpl.class
)
public class JpaConfig {
}
