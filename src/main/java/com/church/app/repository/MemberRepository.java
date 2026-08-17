package com.church.app.repository;

import com.church.app.entity.FamilyRole;
import com.church.app.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Counts one church explicitly, for the platform user browsing parishes they have
     * not entered. {@code count()} would answer for the current tenant scope instead.
     */
    long countByChurchIdAndDeletedFlagFalse(Long churchId);

    /** How many parishioners belong to one anbiyam -- shown on the anbiyam list. */
    long countByAnbiyamIdAndDeletedFlagFalse(Long anbiyamId);

    /** How many people are in one household -- shown on the family list. */
    long countByFamilyIdAndDeletedFlagFalse(Long familyId);

    /**
     * One page of members, searched and filtered in the database.
     *
     * <p>The search runs over every member of the parish and the page is taken from the
     * <em>results</em> -- not the other way round. Filtering a page that was already cut
     * to fifty rows would report "not found" for someone who is plainly in the register,
     * which is worse than having no search at all.
     *
     * <p>Every parameter is optional: null means "do not narrow by this". Text is matched
     * case-insensitively on any part of the value, so "pet" finds Peter, and Tamil works
     * unchanged because the columns are utf8mb4.
     *
     * <p>The ordering keeps a household together and reads it in its own order -- head,
     * spouse, children, then a parent living with them. That cannot be expressed as a
     * plain sort, because the role is stored by name and would come out alphabetically.
     */
    @Query(value = """
            SELECT m FROM Member m
            WHERE m.deletedFlag = false
              AND m.church.id = :churchId
              AND (:familyId IS NULL OR m.family.id = :familyId)
              AND (:anbiyamId IS NULL OR m.anbiyam.id = :anbiyamId)
              AND (:familyRole IS NULL OR m.familyRole = :familyRole)
              AND (:name IS NULL OR LOWER(CONCAT(m.firstName, ' ', COALESCE(m.lastName, '')))
                   LIKE CONCAT('%', :name, '%'))
              AND (:family IS NULL OR LOWER(CONCAT(m.family.familyName, ' ', m.family.familyCode))
                   LIKE CONCAT('%', :family, '%'))
              AND (:anbiyam IS NULL OR LOWER(m.anbiyam.anbiyamName)
                   LIKE CONCAT('%', :anbiyam, '%'))
              AND (:mobile IS NULL OR COALESCE(m.mobile, '') LIKE CONCAT('%', :mobile, '%'))
            ORDER BY m.family.familyName ASC,
                     CASE
                         WHEN m.familyRole = com.church.app.entity.FamilyRole.HEAD THEN 0
                         WHEN m.familyRole = com.church.app.entity.FamilyRole.SPOUSE THEN 1
                         WHEN m.familyRole = com.church.app.entity.FamilyRole.CHILD THEN 2
                         WHEN m.familyRole = com.church.app.entity.FamilyRole.FATHER THEN 3
                         WHEN m.familyRole = com.church.app.entity.FamilyRole.MOTHER THEN 4
                         ELSE 5
                     END ASC,
                     m.firstName ASC
            """,
            countQuery = """
            SELECT COUNT(m) FROM Member m
            WHERE m.deletedFlag = false
              AND m.church.id = :churchId
              AND (:familyId IS NULL OR m.family.id = :familyId)
              AND (:anbiyamId IS NULL OR m.anbiyam.id = :anbiyamId)
              AND (:familyRole IS NULL OR m.familyRole = :familyRole)
              AND (:name IS NULL OR LOWER(CONCAT(m.firstName, ' ', COALESCE(m.lastName, '')))
                   LIKE CONCAT('%', :name, '%'))
              AND (:family IS NULL OR LOWER(CONCAT(m.family.familyName, ' ', m.family.familyCode))
                   LIKE CONCAT('%', :family, '%'))
              AND (:anbiyam IS NULL OR LOWER(m.anbiyam.anbiyamName)
                   LIKE CONCAT('%', :anbiyam, '%'))
              AND (:mobile IS NULL OR COALESCE(m.mobile, '') LIKE CONCAT('%', :mobile, '%'))
            """)
    Page<Member> search(@Param("churchId") Long churchId,
                        @Param("familyId") Long familyId,
                        @Param("anbiyamId") Long anbiyamId,
                        @Param("familyRole") FamilyRole familyRole,
                        @Param("name") String name,
                        @Param("family") String family,
                        @Param("anbiyam") String anbiyam,
                        @Param("mobile") String mobile,
                        Pageable pageable);

    /**
     * The members of one anbiyam.
     *
     * <p>The animator is chosen from these and no one else: whoever leads an anbiyam
     * belongs to it.
     */
    List<Member> findByAnbiyamIdAndDeletedFlagFalseOrderByFirstNameAsc(Long anbiyamId);

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
