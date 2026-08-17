package com.church.app.repository;

import com.church.app.entity.DueStatus;
import com.church.app.entity.PaymentDue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentDueRepository extends JpaRepository<PaymentDue, Long> {

    /** Used when generating dues, so pressing "generate March" twice is harmless. */
    Optional<PaymentDue> findByFamilyIdAndDueYearAndDueMonth(Long familyId, Short dueYear, Byte dueMonth);

    boolean existsByFamilyIdAndDueYearAndDueMonth(Long familyId, Short dueYear, Byte dueMonth);

    /** Oldest first -- payments are normally applied to the longest-standing arrears. */
    @Query("""
            SELECT d FROM PaymentDue d
            WHERE d.family.id = :familyId AND d.deletedFlag = false AND d.status <> 'PAID'
            ORDER BY d.dueYear ASC, d.dueMonth ASC
            """)
    List<PaymentDue> findOutstandingByFamily(@Param("familyId") Long familyId);

    List<PaymentDue> findByFamilyIdAndDeletedFlagFalseOrderByDueYearAscDueMonthAsc(Long familyId);

    List<PaymentDue> findByChurchIdAndDueYearAndDueMonthAndDeletedFlagFalse(
            Long churchId, Short dueYear, Byte dueMonth);

    List<PaymentDue> findByChurchIdAndStatusAndDeletedFlagFalse(Long churchId, DueStatus status);

    /**
     * Every due of one kind in the parish -- the opening balances, for the cutover screen.
     *
     * <p>One query matched up in memory beats a lookup per family: six hundred households
     * is six hundred round trips otherwise.
     */
    List<PaymentDue> findByChurchIdAndDueTypeAndDeletedFlagFalse(
            Long churchId, com.church.app.entity.DueType dueType);

    /**
     * "How much is each family behind, and by how many months."
     *
     * <p>One of the two figures asked for: pending money-wise and month-wise, per family.
     */
    @Query("""
            SELECT f.id            AS familyId,
                   f.familyCode    AS familyCode,
                   f.familyName    AS familyName,
                   COUNT(d)        AS pendingMonths,
                   SUM(d.amountDue - d.amountPaid) AS pendingAmount
            FROM PaymentDue d JOIN d.family f
            WHERE d.church.id = :churchId AND d.deletedFlag = false AND d.status <> 'PAID'
            GROUP BY f.id, f.familyCode, f.familyName
            ORDER BY SUM(d.amountDue - d.amountPaid) DESC
            """)
    List<FamilyArrears> findArrearsByChurch(@Param("churchId") Long churchId);

    /**
     * "What was billed, collected and left outstanding, month by month."
     */
    @Query("""
            SELECT d.dueYear          AS dueYear,
                   d.dueMonth         AS dueMonth,
                   SUM(d.amountDue)   AS billed,
                   SUM(d.amountPaid)  AS collected,
                   SUM(d.amountDue - d.amountPaid) AS pending
            FROM PaymentDue d
            WHERE d.church.id = :churchId AND d.deletedFlag = false
            GROUP BY d.dueYear, d.dueMonth
            ORDER BY d.dueYear ASC, d.dueMonth ASC
            """)
    List<MonthlyCollection> findMonthlySummaryByChurch(@Param("churchId") Long churchId);

    @Query("""
            SELECT COALESCE(SUM(d.amountDue - d.amountPaid), 0)
            FROM PaymentDue d
            WHERE d.church.id = :churchId AND d.deletedFlag = false AND d.status <> 'PAID'
            """)
    BigDecimal totalOutstandingForChurch(@Param("churchId") Long churchId);

    /** Arrears for one family, money-wise and month-wise. */
    interface FamilyArrears {
        Long getFamilyId();

        String getFamilyCode();

        String getFamilyName();

        Long getPendingMonths();

        BigDecimal getPendingAmount();
    }

    /** One month's billing and collection across a parish. */
    interface MonthlyCollection {
        Short getDueYear();

        Byte getDueMonth();

        BigDecimal getBilled();

        BigDecimal getCollected();

        BigDecimal getPending();
    }
}
