package com.church.app.repository;

import com.church.app.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyRepository extends JpaRepository<Family, Long> {

    List<Family> findByChurchIdAndDeletedFlagFalse(Long churchId);

    long countByChurchIdAndDeletedFlagFalse(Long churchId);

    /**
     * Families belong to an anbiyam directly -- {@code family.anbiyam_id} -- so this is
     * the authoritative count. Deriving it from members would miss a family whose members
     * were recorded against a different anbiyam.
     */
    long countByAnbiyamIdAndDeletedFlagFalse(Long anbiyamId);

    /**
     * One page of families, searched in the database.
     *
     * <p>Six hundred households is a realistic parish, so the search has to run over all
     * of them and the page be cut from the results -- not the other way round.
     */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT f FROM Family f
            WHERE f.deletedFlag = false
              AND f.church.id = :churchId
              AND (:name IS NULL OR LOWER(f.familyName) LIKE CONCAT('%', :name, '%'))
              AND (:code IS NULL OR LOWER(f.familyCode) LIKE CONCAT('%', :code, '%'))
              AND (:anbiyam IS NULL OR LOWER(f.anbiyam.anbiyamName) LIKE CONCAT('%', :anbiyam, '%'))
            ORDER BY f.familyName ASC
            """,
            countQuery = """
            SELECT COUNT(f) FROM Family f
            WHERE f.deletedFlag = false
              AND f.church.id = :churchId
              AND (:name IS NULL OR LOWER(f.familyName) LIKE CONCAT('%', :name, '%'))
              AND (:code IS NULL OR LOWER(f.familyCode) LIKE CONCAT('%', :code, '%'))
              AND (:anbiyam IS NULL OR LOWER(f.anbiyam.anbiyamName) LIKE CONCAT('%', :anbiyam, '%'))
            """)
    org.springframework.data.domain.Page<Family> search(
            @org.springframework.data.repository.query.Param("churchId") Long churchId,
            @org.springframework.data.repository.query.Param("name") String name,
            @org.springframework.data.repository.query.Param("code") String code,
            @org.springframework.data.repository.query.Param("anbiyam") String anbiyam,
            org.springframework.data.domain.Pageable pageable);

    List<Family> findByAnbiyamIdAndDeletedFlagFalse(Long anbiyamId);

    /** Family codes restart per parish, so the church must be part of the lookup. */
    Optional<Family> findByChurchIdAndFamilyCode(Long churchId, String familyCode);

    boolean existsByChurchIdAndFamilyCode(Long churchId, String familyCode);
}
