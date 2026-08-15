package com.church.app.repository;

import com.church.app.entity.Church;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChurchRepository extends JpaRepository<Church, Long> {

    Optional<Church> findByUuid(String uuid);

    List<Church> findByDeletedFlagFalseOrderByChurchNameAsc();

    /**
     * The parishes a platform user may enter.
     *
     * <p>Only stations appear: a substation holds no members, families, anbiyams or
     * priest, so there is nothing there to administer.
     */
    List<Church> findByParentChurchIsNullAndDeletedFlagFalseOrderByChurchNameAsc();

    /** The outstation chapels under one station. */
    List<Church> findByParentChurchIdAndDeletedFlagFalseOrderByChurchNameAsc(Long parentChurchId);
}
