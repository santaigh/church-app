package com.church.app.repository;

import com.church.app.entity.DueStatus;
import com.church.app.entity.Payment;
import com.church.app.entity.PaymentAllocation;
import com.church.app.entity.PaymentDue;
import com.church.app.entity.PaymentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the three cases that drove the design: a payment spanning several months,
 * a partial payment, and month-wise arrears.
 *
 * <p>Read-only against the seeded sample data.
 */
@SpringBootTest
@Transactional
class PaymentTests {

    @Autowired
    private PaymentDueRepository paymentDueRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private ReceiptSequenceRepository receiptSequenceRepository;

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("one receipt clears three months, the last of them only partially")
    void oneReceiptSpansThreeMonths() {
        Payment receipt = paymentRepository
                .findByChurchIdAndReceiptYearAndReceiptNo(1L, (short) 2026, "R-2026-0001").orElseThrow();
        assertEquals(0, money("500.00").compareTo(receipt.getAmount()));
        assertEquals(PaymentMode.CASH, receipt.getPaymentMode());

        List<PaymentAllocation> allocations =
                paymentAllocationRepository.findByPaymentIdOrderByPeriod(receipt.getId());
        assertEquals(3, allocations.size(), "receipt should cover January, February and March");

        // 200 + 200 + 100 = the full 500 handed over.
        assertEquals(0, money("200.00").compareTo(allocations.get(0).getAllocatedAmount()));
        assertEquals(0, money("200.00").compareTo(allocations.get(1).getAllocatedAmount()));
        assertEquals(0, money("100.00").compareTo(allocations.get(2).getAllocatedAmount()));
        assertFalse(receipt.hasUnallocatedAmount());

        // The receipt prints the months it covers.
        assertEquals("January 2026", allocations.get(0).getPaymentDue().getPeriodLabel());
        assertEquals("March 2026", allocations.get(2).getPaymentDue().getPeriodLabel());
    }

    @Test
    @DisplayName("the part-paid month is PARTIAL with the right balance")
    void partialMonthIsTracked() {
        PaymentDue march = paymentDueRepository
                .findByFamilyIdAndDueYearAndDueMonth(1L, (short) 2026, (byte) 3).orElseThrow();

        assertEquals(DueStatus.PARTIAL, march.getStatus());
        assertEquals(0, money("200.00").compareTo(march.getAmountDue()));
        assertEquals(0, money("100.00").compareTo(march.getAmountPaid()));
        assertEquals(0, money("100.00").compareTo(march.getBalance()));
        assertTrue(march.isOutstanding());

        PaymentDue january = paymentDueRepository
                .findByFamilyIdAndDueYearAndDueMonth(1L, (short) 2026, (byte) 1).orElseThrow();
        assertEquals(DueStatus.PAID, january.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(january.getBalance()));
    }

    @Test
    @DisplayName("arrears per family, money-wise and month-wise")
    void arrearsPerFamily() {
        List<PaymentDueRepository.FamilyArrears> arrears =
                paymentDueRepository.findArrearsByChurch(1L);

        // Fernando paid in full and must not appear at all.
        assertFalse(arrears.stream().anyMatch(a -> "FAM-003".equals(a.getFamilyCode())));

        var devasagayam = arrears.stream()
                .filter(a -> "FAM-002".equals(a.getFamilyCode())).findFirst().orElseThrow();
        assertEquals(3L, devasagayam.getPendingMonths());
        assertEquals(0, money("900.00").compareTo(devasagayam.getPendingAmount()));

        var arulraj = arrears.stream()
                .filter(a -> "FAM-001".equals(a.getFamilyCode())).findFirst().orElseThrow();
        assertEquals(1L, arulraj.getPendingMonths());
        assertEquals(0, money("100.00").compareTo(arulraj.getPendingAmount()));

        // Ordered by who owes most.
        assertEquals("FAM-002", arrears.get(0).getFamilyCode());
    }

    @Test
    @DisplayName("month-wise billed, collected and pending across the parish")
    void monthlySummary() {
        List<PaymentDueRepository.MonthlyCollection> summary =
                paymentDueRepository.findMonthlySummaryByChurch(1L);
        assertEquals(3, summary.size());

        // March: billed 200+300+150 = 650, collected 100+0+150 = 250.
        var march = summary.get(2);
        assertEquals((byte) 3, march.getDueMonth());
        assertEquals(0, money("650.00").compareTo(march.getBilled()));
        assertEquals(0, money("250.00").compareTo(march.getCollected()));
        assertEquals(0, money("400.00").compareTo(march.getPending()));

        // Whole parish: 1950 billed, 950 collected, 1000 outstanding.
        assertEquals(0, money("1000.00").compareTo(paymentDueRepository.totalOutstandingForChurch(1L)));
    }

    @Test
    @DisplayName("the model's invariant holds: amount_paid equals the sum of allocations")
    void allocationsReconcileToDues() {
        for (PaymentDue due : paymentDueRepository
                .findByFamilyIdAndDeletedFlagFalseOrderByDueYearAscDueMonthAsc(1L)) {
            assertEquals(0,
                    paymentAllocationRepository.sumAllocatedForDue(due.getId())
                            .compareTo(due.getAmountPaid()),
                    "allocations disagree with amount_paid on due " + due.getId());
        }
    }

    @Test
    @DisplayName("outstanding dues come back oldest first, for applying a new payment")
    void outstandingOldestFirst() {
        List<PaymentDue> outstanding = paymentDueRepository.findOutstandingByFamily(2L);
        assertEquals(3, outstanding.size());
        assertEquals((byte) 1, outstanding.get(0).getDueMonth());
        assertEquals((byte) 3, outstanding.get(2).getDueMonth());
    }

    @Test
    @DisplayName("receipt books are per parish: two churches both hold R-2026-0001")
    void receiptNumbersAreScopedPerChurch() {
        Payment stMarys = paymentRepository
                .findByChurchIdAndReceiptYearAndReceiptNo(1L, (short) 2026, "R-2026-0001").orElseThrow();
        Payment stJosephs = paymentRepository
                .findByChurchIdAndReceiptYearAndReceiptNo(2L, (short) 2026, "R-2026-0001").orElseThrow();

        assertFalse(stMarys.getId().equals(stJosephs.getId()));
        assertEquals(0, money("500.00").compareTo(stMarys.getAmount()));
        assertEquals(0, money("250.00").compareTo(stJosephs.getAmount()));
    }

    @Test
    @DisplayName("the receipt counter formats and advances correctly")
    void receiptSequenceAdvances() {
        var sequence = receiptSequenceRepository
                .findByChurchIdAndSequenceYear(1L, (short) 2026).orElseThrow();
        assertEquals(2, sequence.getLastNumber());
        assertEquals("R-2026-0003", sequence.nextReceiptNo());
        assertEquals(3, sequence.getLastNumber());
    }

    @Test
    @DisplayName("collection totals ignore voided receipts")
    void collectedTotalsForDateRange() {
        BigDecimal collected = paymentRepository.totalCollected(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(0, money("950.00").compareTo(collected));
    }
}
