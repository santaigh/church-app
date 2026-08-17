package com.church.app.service;

import com.church.app.dto.PaymentForm;
import com.church.app.entity.ActorType;
import com.church.app.entity.DueStatus;
import com.church.app.entity.DueType;
import com.church.app.entity.PaymentDue;
import com.church.app.entity.PaymentMode;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.PaymentDueRepository;
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
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arrears a parish brings in on the day it starts using this.
 *
 * <p>Reconstructing years of history would mean inventing amounts nobody charged, so each
 * family gets one line instead -- and these tests are about that line behaving like any
 * other debt once it exists.
 */
@SpringBootTest
@Transactional
class OpeningBalanceTests {

    private static final Long CHENNAI = 1L;
    private static final Long FAM_TWO = 2L;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentDueRepository paymentDueRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @BeforeEach
    void signIn() {
        TenantContext.setChurch(CHENNAI);
        AppUserPrincipal principal = AppUserPrincipal.builder()
                .userId(4L).churchId(CHENNAI).churchName("St. Mary's Cathedral")
                .actorType(ActorType.MEMBER).username("admin@example.com").password("x")
                .displayName("Admin").role("AppSA")
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

    private PaymentDue openingBalance(Long familyId) {
        return paymentDueRepository
                .findByFamilyIdAndDeletedFlagFalseOrderByDueYearAscDueMonthAsc(familyId).stream()
                .filter(due -> due.getDueType() == DueType.OPENING_BALANCE)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("an opening balance is dated the month before the family starts paying")
    void openingBalanceSitsBeforeTheFirstMonth() {
        paymentService.setOpeningBalance(FAM_TWO, new BigDecimal("4500.00"));

        PaymentDue opening = openingBalance(FAM_TWO);
        // Every sample family starts 2026-01-01, so the slot is December 2025 -- a month
        // no generated due can ever occupy.
        assertEquals(YearMonth.of(2025, 12),
                YearMonth.of(opening.getDueYear(), opening.getDueMonth()));
        assertEquals(new BigDecimal("4500.00"), opening.getAmountDue());
        assertEquals(DueType.OPENING_BALANCE, opening.getDueType());
    }

    @Test
    @DisplayName("generated months are marked as ordinary, not as arrears carried forward")
    void generatedMonthsAreMonthly() {
        paymentService.generateDues(YearMonth.of(2026, 6));

        PaymentDue june = paymentDueRepository
                .findByFamilyIdAndDueYearAndDueMonth(FAM_TWO, (short) 2026, (byte) 6).orElseThrow();
        assertEquals(DueType.MONTHLY, june.getDueType());
    }

    @Test
    @DisplayName("arrears carried forward are settled before anything current")
    void openingBalanceIsSettledFirst() {
        paymentService.setOpeningBalance(FAM_TWO, new BigDecimal("450.00"));

        PaymentForm form = new PaymentForm();
        form.setFamilyId(FAM_TWO);
        form.setAmount(new BigDecimal("450.00"));
        form.setPaymentMode(PaymentMode.CASH);
        paymentService.collect(form);

        assertEquals(DueStatus.PAID, openingBalance(FAM_TWO).getStatus(),
                "the old book is cleared first");
        // January 2026, the first real month, is untouched.
        assertEquals(DueStatus.PENDING, paymentDueRepository
                .findByFamilyIdAndDueYearAndDueMonth(FAM_TWO, (short) 2026, (byte) 1)
                .orElseThrow().getStatus());
    }

    @Test
    @DisplayName("the figure can be corrected until money lands on it")
    void balanceIsEditableUntilPaid() {
        paymentService.setOpeningBalance(FAM_TWO, new BigDecimal("4500.00"));
        paymentService.setOpeningBalance(FAM_TWO, new BigDecimal("4000.00"));

        assertEquals(new BigDecimal("4000.00"), openingBalance(FAM_TWO).getAmountDue());
    }

    @Test
    @DisplayName("once part-paid it locks, because a receipt was written against it")
    void balanceLocksOncePaid() {
        paymentService.setOpeningBalance(FAM_TWO, new BigDecimal("1000.00"));

        PaymentForm form = new PaymentForm();
        form.setFamilyId(FAM_TWO);
        form.setAmount(new BigDecimal("200.00"));
        form.setPaymentMode(PaymentMode.CASH);
        paymentService.collect(form);

        assertThrows(RuntimeException.class,
                () -> paymentService.setOpeningBalance(FAM_TWO, new BigDecimal("900.00")),
                "rewriting it would contradict the family's own paper");
    }

    @Test
    @DisplayName("a credit is not a negative debt")
    void negativeBalancesAreRefused() {
        assertThrows(RuntimeException.class,
                () -> paymentService.setOpeningBalance(FAM_TWO, new BigDecimal("-500.00")));
    }

    @Test
    @DisplayName("setting it back to zero removes the line rather than leaving a nil debt")
    void zeroRemovesTheLine() {
        paymentService.setOpeningBalance(FAM_TWO, new BigDecimal("500.00"));
        paymentService.setOpeningBalance(FAM_TWO, BigDecimal.ZERO);

        assertTrue(paymentDueRepository
                .findByFamilyIdAndDeletedFlagFalseOrderByDueYearAscDueMonthAsc(FAM_TWO).stream()
                .noneMatch(due -> due.getDueType() == DueType.OPENING_BALANCE));
    }

    @Test
    @DisplayName("the cutover screen lists every family, whether or not it owes anything")
    void screenListsEveryFamily() {
        paymentService.setOpeningBalance(FAM_TWO, new BigDecimal("300.00"));

        var paged = paymentService.openingBalances(1, 50);
        assertEquals(familyRepository.findByChurchIdAndDeletedFlagFalse(CHENNAI).size(),
                paged.totalRows());
        assertTrue(paged.rows().stream().anyMatch(r -> r.familyId().equals(FAM_TWO)
                && r.balance().compareTo(new BigDecimal("300.00")) == 0));
    }
}
