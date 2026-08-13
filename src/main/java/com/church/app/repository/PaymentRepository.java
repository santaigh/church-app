package com.church.app.repository;

import com.church.app.entity.Payment;
import com.church.app.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Receipt numbers are unique per parish per year, so all three are needed. */
    Optional<Payment> findByChurchIdAndReceiptYearAndReceiptNo(Long churchId, Short receiptYear, String receiptNo);

    boolean existsByChurchIdAndReceiptYearAndReceiptNo(Long churchId, Short receiptYear, String receiptNo);

    Page<Payment> findByChurchIdAndDeletedFlagFalseOrderByReceiptDateDesc(Long churchId, Pageable pageable);

    List<Payment> findByFamilyIdAndDeletedFlagFalseOrderByReceiptDateDesc(Long familyId);

    List<Payment> findByChurchIdAndStatusAndDeletedFlagFalse(Long churchId, PaymentStatus status);

    /** Receipts with money not yet applied to any month -- advance credit sitting unused. */
    @Query("""
            SELECT p FROM Payment p
            WHERE p.church.id = :churchId AND p.deletedFlag = false
              AND p.status = 'ACTIVE' AND p.allocatedAmount < p.amount
            ORDER BY p.receiptDate ASC
            """)
    List<Payment> findWithUnallocatedAmount(@Param("churchId") Long churchId);

    /**
     * Cash actually collected in a date range. Voided receipts are excluded -- they
     * remain visible as records but must never count towards collection totals.
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.church.id = :churchId AND p.deletedFlag = false AND p.status = 'ACTIVE'
              AND p.receiptDate BETWEEN :from AND :to
            """)
    BigDecimal totalCollected(@Param("churchId") Long churchId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);
}
