package com.church.app.repository;

import com.church.app.entity.MemberExt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberExtRepository extends JpaRepository<MemberExt, Long> {

    /** At most one row per member -- guaranteed by the unique key V16 added. */
    Optional<MemberExt> findByMemberIdAndDeletedFlagFalse(Long memberId);
}
