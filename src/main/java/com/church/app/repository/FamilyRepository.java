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

    List<Family> findByAnbiyamIdAndDeletedFlagFalse(Long anbiyamId);

    /** Family codes restart per parish, so the church must be part of the lookup. */
    Optional<Family> findByChurchIdAndFamilyCode(Long churchId, String familyCode);

    boolean existsByChurchIdAndFamilyCode(Long churchId, String familyCode);
}
