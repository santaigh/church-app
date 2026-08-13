package com.church.app.repository;

import com.church.app.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * Resolves a login identifier to a single member.
     *
     * <p>Takes the email and the mobile separately because the mobile must already have
     * been normalised by the caller -- {@code 9840100001} has to reach the account stored
     * as {@code +919840100001}. Passing the raw typed string for both would only match
     * when the user happened to type the exact stored format.
     *
     * <p>Pass null for whichever the identifier is not; a null never matches a column.
     *
     * <p>Both columns are UNIQUE and soft-deleted rows are excluded, so at most one row
     * can match, which is why this returns {@link Optional} rather than a list.
     */
    @Query("""
            SELECT m FROM Member m
            WHERE m.deletedFlag = false
              AND (LOWER(m.email) = LOWER(:email) OR m.mobile = :mobile)
            """)
    Optional<Member> findByEmailOrMobile(@Param("email") String email, @Param("mobile") String mobile);

    Optional<Member> findByEmailIgnoreCase(String email);

    Optional<Member> findByMobile(String mobile);

    List<Member> findByChurchIdAndDeletedFlagFalse(Long churchId);

    List<Member> findByFamilyIdAndDeletedFlagFalse(Long familyId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByMobile(String mobile);

    // ------------------------------------------------------------ lockout counters

    /**
     * Increments the failure counter in a single statement.
     *
     * <p>Read-then-write would lose counts: five simultaneous wrong guesses would each
     * read 4 and each write 5, so the account would never reach the threshold.
     */
    @Modifying
    @Query("UPDATE Member m SET m.failedAttemptCount = m.failedAttemptCount + 1 WHERE m.id = :id")
    int incrementFailedAttempts(@Param("id") Long id);

    /**
     * Locks the account only if it has reached the threshold and is not already locked.
     *
     * @return 1 when this call is the one that locked it, 0 otherwise -- which is how the
     *         caller knows to write a single ACCOUNT_LOCKED audit entry rather than one
     *         per subsequent attempt
     */
    @Modifying
    @Query("""
            UPDATE Member m SET m.lockedAt = :now
            WHERE m.id = :id AND m.lockedAt IS NULL AND m.failedAttemptCount >= :threshold
            """)
    int lockIfThresholdReached(@Param("id") Long id,
                               @Param("threshold") int threshold,
                               @Param("now") LocalDateTime now);

    /** Clears the counter and stamps the sign-in time after a successful authentication. */
    @Modifying
    @Query("UPDATE Member m SET m.failedAttemptCount = 0, m.lastLoginAt = :now WHERE m.id = :id")
    int recordSuccessfulLogin(@Param("id") Long id, @Param("now") LocalDateTime now);
}
