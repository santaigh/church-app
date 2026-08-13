package com.church.app.repository;

import com.church.app.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    /** The months a receipt covers -- exactly what gets printed on it. */
    @Query("""
            SELECT a FROM PaymentAllocation a
            JOIN FETCH a.paymentDue d
            WHERE a.payment.id = :paymentId
            ORDER BY d.dueYear ASC, d.dueMonth ASC
            """)
    List<PaymentAllocation> findByPaymentIdOrderByPeriod(@Param("paymentId") Long paymentId);

    /** Every payment that contributed to one month -- a due may be settled in instalments. */
    List<PaymentAllocation> findByPaymentDueId(Long paymentDueId);

    /** Removes a voided receipt's allocations; the dues are restored alongside. */
    void deleteByPaymentId(Long paymentId);

    /**
     * Verifies the model's central invariant: a due's {@code amountPaid} must always
     * equal the sum of its allocations.
     */
    @Query("""
            SELECT COALESCE(SUM(a.allocatedAmount), 0) FROM PaymentAllocation a
            WHERE a.paymentDue.id = :dueId
            """)
    BigDecimal sumAllocatedForDue(@Param("dueId") Long dueId);
}
