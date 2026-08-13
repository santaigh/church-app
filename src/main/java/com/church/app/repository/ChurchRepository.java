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
}
