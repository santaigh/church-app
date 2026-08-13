package com.church.app.repository;

import com.church.app.entity.SaasUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SaasUserRepository extends JpaRepository<SaasUser, Long> {

    /**
     * Same email-or-mobile resolution as members, against the platform account table.
     * The mobile must already be normalised by the caller.
     */
    @Query("""
            SELECT s FROM SaasUser s
            WHERE s.deletedFlag = false
              AND (LOWER(s.email) = LOWER(:email) OR s.mobile = :mobile)
            """)
    Optional<SaasUser> findByEmailOrMobile(@Param("email") String email, @Param("mobile") String mobile);

    Optional<SaasUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    // ------------------------------------------------------------ lockout counters
    // Mirrors MemberRepository; see there for why these are single-statement updates.

    @Modifying
    @Query("UPDATE SaasUser s SET s.failedAttemptCount = s.failedAttemptCount + 1 WHERE s.id = :id")
    int incrementFailedAttempts(@Param("id") Long id);

    /** @return 1 when this call is the one that locked the account, 0 otherwise */
    @Modifying
    @Query("""
            UPDATE SaasUser s SET s.lockedAt = :now
            WHERE s.id = :id AND s.lockedAt IS NULL AND s.failedAttemptCount >= :threshold
            """)
    int lockIfThresholdReached(@Param("id") Long id,
                               @Param("threshold") int threshold,
                               @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE SaasUser s SET s.failedAttemptCount = 0, s.lastLoginAt = :now WHERE s.id = :id")
    int recordSuccessfulLogin(@Param("id") Long id, @Param("now") LocalDateTime now);
}
