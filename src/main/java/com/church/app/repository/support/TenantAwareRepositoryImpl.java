package com.church.app.repository.support;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

/**
 * Base class for every repository, closing the one hole a Hibernate filter leaves open.
 *
 * <p>Hibernate filters are <b>not applied to primary-key loads</b>. {@code findById} in
 * {@link SimpleJpaRepository} calls {@code EntityManager.find()}, which bypasses filters
 * entirely -- so a Chennai user requesting {@code /member/7} would still be handed a
 * Madurai member. That is precisely the direct-URL case tenant filtering exists to stop,
 * and it is easy to assume the filter already covers it.
 *
 * <p>Re-routing the lookup through a criteria query puts it back on the query path, where
 * the filter does apply. The result is identical for a legitimate request and empty for a
 * cross-tenant one.
 *
 * <p>Registered for all repositories via {@code @EnableJpaRepositories(repositoryBaseClass)}.
 * Entities that are not tenant-scoped -- {@code Church}, {@code Role}, {@code SaasUser} --
 * have no filter defined, so this changes nothing for them.
 */
public class TenantAwareRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;

    public TenantAwareRepositoryImpl(JpaEntityInformation<T, ?> entityInformation,
                                     EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
    }

    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        String idAttribute = entityInformation.getIdAttribute() != null
                ? entityInformation.getIdAttribute().getName()
                : "id";
        // A criteria query rather than em.find(), so the tenant filter is honoured.
        return findOne((Specification<T>) (root, query, cb) -> cb.equal(root.get(idAttribute), id));
    }

    /**
     * {@code getReferenceById} returns a lazy proxy resolved by primary key, which would
     * also bypass the filter. Routed through {@link #findById} so a cross-tenant id
     * cannot be dereferenced.
     */
    @Override
    public T getReferenceById(ID id) {
        return findById(id).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "%s with id %s not found in the current tenant scope"
                        .formatted(getDomainClass().getSimpleName(), id)));
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }
}
