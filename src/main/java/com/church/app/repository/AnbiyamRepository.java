package com.church.app.repository;

import com.church.app.entity.Anbiyam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnbiyamRepository extends JpaRepository<Anbiyam, Long> {

    List<Anbiyam> findByChurchIdAndDeletedFlagFalseOrderByAnbiyamNameAsc(Long churchId);

    long countByChurchIdAndDeletedFlagFalse(Long churchId);
}
