package com.church.app.service;

import com.church.app.dto.PaymentForm;
import com.church.app.entity.ActorType;
import com.church.app.entity.DueStatus;
import com.church.app.entity.Payment;
import com.church.app.entity.PaymentDue;
import com.church.app.entity.PaymentMode;
import com.church.app.entity.PaymentStatus;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.PaymentDueRepository;
import com.church.app.repository.PaymentRepository;
import com.church.app.security.AppUserPrincipal;
import com.church.app.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money rules, proved before any screen can reach them.
 *
 * <p>Transactional, so nothing written here survives -- the sample register is asserted
 * on by other tests, and a stray receipt would move every total.
 *
 * <p>Sample data this leans on: FAM-001 owes 200 for March and has paid 100. FAM-002 owes
 * January, February and March at 300 each, nothing paid.
 */
@SpringBootTest
@Transactional
class PaymentServiceTests {

    private static final Long CHENNAI = 1L;
    private static final Long FAM_ONE = 1L;
    private static final Long FAM_TWO = 2L;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentDueRepository paymentDueRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @BeforeEach
    void signInAsCollector() {
        TenantContext.setChurch(CHENNAI);
        // Member 4, Stephen, with the permissions of a parish administrator.
        AppUserPrincipal principal = AppUserPrincipal.builder()
                .userId(4L).churchId(CHENNAI).churchName("St. Mary's Cathedral")
                .actorType(ActorType.MEMBER).username("collector@example.com").password("x")
                .displayName("Collector").role("AppAdmin")
                .usingDefaultPassword(false).locked(false).active(true)
                .permission("PERM_PAYMENT_ADD").permission("PERM_PAYMENT_EDIT")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private PaymentForm form(Long familyId, String amount) {
        PaymentForm form = new PaymentForm();
        form.setFamilyId(familyId);
        form.setAmount(new BigDecimal(amount));
        form.setPaymentMode(PaymentMode.CASH);
        return form;
    }

    private PaymentDue due(Long familyId, int year, int month) {
        return paymentDueRepository
                .findByFamilyIdAndDueYearAndDueMonth(familyId, (short) year, (byte) month)
                .orElseThrow();
    }

    // ------------------------------------------------------------- allocation

    @Test
    @DisplayName("money settles the oldest month first, and the collector never chooses")
    void oldestMonthIsSettledFirst() {
        // FAM-002 owes Jan, Feb, Mar at 300. Pay 500.
        PaymentService.CollectionResult result = paymentService.collect(form(FAM_TWO, "500.00"));

        assertEquals(DueStatus.PAID, due(FAM_TWO, 2026, 1).getStatus(), "January settled in full");
        assertEquals(DueStatus.PARTIAL, due(FAM_TWO, 2026, 2).getStatus(), "February part-paid");
        assertEquals(new BigDecimal("200.00"), due(FAM_TWO, 2026, 2).getAmountPaid());
        assertEquals(DueStatus.PENDING, due(FAM_TWO, 2026, 3).getStatus(), "March untouched");

        assertTrue(result.periodsSettled().contains("January 2026"));
    }

    @Test
    @DisplayName("a part-paid month is finished before the next is started")
    void partialMonthIsCompletedFirst() {
        // FAM-001 owes 100 of March's 200.
        paymentService.collect(form(FAM_ONE, "100.00"));

        assertEquals(DueStatus.PAID, due(FAM_ONE, 2026, 3).getStatus());
        assertEquals(new BigDecimal("200.00"), due(FAM_ONE, 2026, 3).getAmountPaid());
    }

    @Test
    @DisplayName("paying more than is owed runs the months forward")
    void advancePaymentGeneratesFutureDues() {
        // FAM-002 owes 900 across three months at 300. Pay 1500: 900 clears the arrears,
        // and 600 runs two months forward.
        PaymentService.CollectionResult result = paymentService.collect(form(FAM_TWO, "1500.00"));

        assertEquals(new BigDecimal("600.00"), result.paidForward());

        List<PaymentDue> all = paymentDueRepository
                .findByFamilyIdAndDeletedFlagFalseOrderByDueYearAscDueMonthAsc(FAM_TWO);
        assertEquals(5, all.size(), "three original months plus two generated forward");
        assertTrue(all.stream().allMatch(d -> d.getStatus() == DueStatus.PAID),
                "every month it reached is settled");
    }

    // ---------------------------------------------------------------- receipts

    @Test
    @DisplayName("receipt numbers run on from the parish's own sequence")
    void receiptNumbersAreSequentialPerParish() {
        // Chennai's counter stands at 2 in the sample data.
        String first = paymentService.collect(form(FAM_TWO, "100.00")).receiptNo();
        String second = paymentService.collect(form(FAM_TWO, "100.00")).receiptNo();

        assertEquals("R-2026-0003", first);
        assertEquals("R-2026-0004", second);
    }

    @Test
    @DisplayName("the collector is recorded, so the register says who took the money")
    void collectorIsRecorded() {
        Long id = paymentService.collect(form(FAM_TWO, "100.00")).paymentId();
        Payment payment = paymentRepository.findById(id).orElseThrow();

        assertNotNull(payment.getReceivedBy());
        assertEquals(4L, payment.getReceivedBy().getId());
    }

    @Test
    @DisplayName("a receipt cannot be dated in the future, whoever is asking")
    void futureDatesAreRefused() {
        PaymentForm form = form(FAM_TWO, "100.00");
        form.setReceiptDate(LocalDate.now().plusDays(1));

        assertThrows(RuntimeException.class, () -> paymentService.collect(form),
                "there is no honest reason to write a receipt for next week");
    }

    @Test
    @DisplayName("an administrator may date a receipt in the past")
    void administratorsMayBackdate() {
        PaymentForm form = form(FAM_TWO, "100.00");
        form.setReceiptDate(LocalDate.now().minusDays(2));

        Long id = paymentService.collect(form).paymentId();
        assertEquals(LocalDate.now().minusDays(2),
                paymentRepository.findById(id).orElseThrow().getReceiptDate());
    }

    // -------------------------------------------------------------------- void

    @Test
    @DisplayName("voiding a receipt gives every month its money back")
    void voidingRestoresTheDues() {
        PaymentService.CollectionResult result = paymentService.collect(form(FAM_TWO, "500.00"));
        assertEquals(DueStatus.PAID, due(FAM_TWO, 2026, 1).getStatus());

        paymentService.voidPayment(result.paymentId(), "Wrong amount entered");

        assertEquals(DueStatus.PENDING, due(FAM_TWO, 2026, 1).getStatus(), "January owes again");
        assertEquals(BigDecimal.ZERO.setScale(2), due(FAM_TWO, 2026, 2).getAmountPaid());

        Payment voided = paymentRepository.findById(result.paymentId()).orElseThrow();
        assertEquals(PaymentStatus.VOID, voided.getStatus());
        assertNotNull(voided.getVoidedUser(), "who cancelled it is on record");
    }

    @Test
    @DisplayName("a voided receipt keeps its number, so the book has no gap")
    void voidingDoesNotFreeTheNumber() {
        PaymentService.CollectionResult first = paymentService.collect(form(FAM_TWO, "100.00"));
        paymentService.voidPayment(first.paymentId(), "Mistake");

        String next = paymentService.collect(form(FAM_TWO, "100.00")).receiptNo();

        assertEquals("R-2026-0003", first.receiptNo());
        assertEquals("R-2026-0004", next, "the cancelled number is not handed out again");
        assertTrue(paymentRepository.findById(first.paymentId()).isPresent(),
                "the row stays: a missing number cannot be told apart from a covered-up shortfall");
    }

    @Test
    @DisplayName("voiding without a reason is refused")
    void voidingNeedsAReason() {
        Long id = paymentService.collect(form(FAM_TWO, "100.00")).paymentId();
        assertThrows(RuntimeException.class, () -> paymentService.voidPayment(id, "  "));
    }

    @Test
    @DisplayName("a wrong amount is corrected by reissuing, cross-referenced both ways")
    void voidAndReissueCorrectsAnAmount() {
        // 500 typed where 50 was meant.
        PaymentService.CollectionResult wrong = paymentService.collect(form(FAM_TWO, "500.00"));

        PaymentService.CollectionResult fixed = paymentService.voidAndReissue(
                wrong.paymentId(), "Wrong amount entered", form(FAM_TWO, "50.00"));

        Payment voided = paymentRepository.findById(wrong.paymentId()).orElseThrow();
        Payment reissued = paymentRepository.findById(fixed.paymentId()).orElseThrow();

        assertEquals(PaymentStatus.VOID, voided.getStatus());
        assertTrue(voided.getVoidReason().contains(fixed.receiptNo()),
                "the cancelled receipt points at its replacement");
        assertTrue(reissued.getRemarks().contains(wrong.receiptNo()),
                "and the replacement points back");
        assertEquals(new BigDecimal("50.00"), reissued.getAmount());
    }

    // ---------------------------------------------------------- due generation

    @Test
    @DisplayName("generating dues twice charges nobody twice")
    void dueGenerationIsIdempotent() {
        YearMonth period = YearMonth.of(2026, 4);

        PaymentService.DueGenerationResult first = paymentService.generateDues(period);
        PaymentService.DueGenerationResult second = paymentService.generateDues(period);

        assertTrue(first.created() > 0, "April did not exist");
        assertEquals(0, second.created(), "the second run creates nothing");
        assertEquals(first.created(), second.skipped());
    }

    @Test
    @DisplayName("a family that has not started paying is skipped")
    void familiesBeforeTheirStartDateAreSkipped() {
        // Every sample family starts 2026-01-01, so a month before that yields nothing.
        PaymentService.DueGenerationResult result = paymentService.generateDues(YearMonth.of(2025, 6));
        assertEquals(0, result.created());
    }

    // ------------------------------------------------------- amount changes

    @Test
    @DisplayName("raising the monthly amount changes the unpaid months, not the settled ones")
    void amountChangeLeavesSettledMonthsAlone() {
        paymentService.generateDues(YearMonth.now());
        paymentService.generateDues(YearMonth.now().plusMonths(1));

        BigDecimal paidBefore = due(FAM_ONE, 2026, 1).getAmountDue();
        int changed = paymentService.applyMonthlyAmountChange(FAM_ONE, new BigDecimal("250.00"));

        assertTrue(changed > 0, "future months take the new figure");
        assertEquals(paidBefore, due(FAM_ONE, 2026, 1).getAmountDue(),
                "January is paid and keeps the figure its receipt was written against");
        assertEquals(new BigDecimal("250.00"),
                familyRepository.findById(FAM_ONE).orElseThrow().getMonthlyAmount());
    }

    @Test
    @DisplayName("the monthly amount cannot go below the floor")
    void amountCannotGoBelowTheMinimum() {
        assertThrows(RuntimeException.class,
                () -> paymentService.applyMonthlyAmountChange(FAM_ONE, new BigDecimal("10.00")));
    }
}
