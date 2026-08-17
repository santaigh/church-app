package com.church.app.service;

import com.church.app.config.AppPaymentProperties;
import com.church.app.dto.PaymentForm;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.Church;
import com.church.app.entity.DueStatus;
import com.church.app.entity.DueType;
import com.church.app.entity.Family;
import com.church.app.entity.Operation;
import com.church.app.entity.Payment;
import com.church.app.entity.PaymentAllocation;
import com.church.app.entity.PaymentDue;
import com.church.app.entity.PaymentStatus;
import com.church.app.entity.ReceiptSequence;
import com.church.app.entity.Resource;
import com.church.app.exception.BusinessException;
import com.church.app.exception.ResourceNotFoundException;
import com.church.app.repository.ChurchRepository;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.MemberRepository;
import com.church.app.repository.PaymentAllocationRepository;
import com.church.app.repository.PaymentDueRepository;
import com.church.app.repository.PaymentRepository;
import com.church.app.repository.ReceiptSequenceRepository;
import com.church.app.security.AppUserPrincipal;
import com.church.app.security.CurrentUser;
import com.church.app.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Money owed, money received, and which one settled which.
 *
 * <p>Every rule that decides where a rupee lands is here rather than in a controller, so
 * it holds however the payment arrives -- a collection screen today, a bulk import later.
 *
 * <p>Three rules are worth stating outright, because they are what make the register
 * auditable rather than merely stored:
 *
 * <ul>
 *   <li><b>Oldest first.</b> A collector never chooses which month money lands on, so
 *       "how far behind is this family" always has an answer.</li>
 *   <li><b>Receipt numbers are gapless per parish per year.</b> The counter is locked for
 *       the length of the transaction, so a rolled-back collection consumes nothing.</li>
 *   <li><b>Nothing is deleted.</b> A wrong receipt is voided with a reason and reissued;
 *       the number stays used. A missing number in a receipt book is indistinguishable
 *       from a covered-up shortfall, which is the whole point of gapless numbering.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** How far ahead a single payment may run when it exceeds what is owed. */
    private static final int MAX_ADVANCE_MONTHS = 24;

    private final PaymentRepository paymentRepository;
    private final PaymentDueRepository paymentDueRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ReceiptSequenceRepository receiptSequenceRepository;
    private final FamilyRepository familyRepository;
    private final MemberRepository memberRepository;
    private final ChurchRepository churchRepository;
    private final AppPaymentProperties paymentProperties;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentDueRepository paymentDueRepository,
                          PaymentAllocationRepository paymentAllocationRepository,
                          ReceiptSequenceRepository receiptSequenceRepository,
                          FamilyRepository familyRepository,
                          MemberRepository memberRepository,
                          ChurchRepository churchRepository,
                          AppPaymentProperties paymentProperties,
                          AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.paymentDueRepository = paymentDueRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.receiptSequenceRepository = receiptSequenceRepository;
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
        this.churchRepository = churchRepository;
        this.paymentProperties = paymentProperties;
        this.auditService = auditService;
    }

    /** What a collection did, so the screen can say it rather than guess. */
    public record CollectionResult(Long paymentId,
                                   String receiptNo,
                                   BigDecimal amount,
                                   BigDecimal settledArrears,
                                   BigDecimal paidForward,
                                   List<String> periodsSettled) {
    }

    public record DueGenerationResult(int created, int skipped, YearMonth period) {
    }

    /** A family as the collection screen needs it: who they are and what they owe. */
    public record FamilyDues(Long familyId,
                             String familyCode,
                             String familyName,
                             String anbiyamName,
                             String headName,
                             BigDecimal monthlyAmount,
                             BigDecimal outstanding,
                             List<DueRow> months) {
    }

    public record DueRow(String period, BigDecimal due, BigDecimal paid, BigDecimal balance,
                         DueStatus status) {
    }

    /** One line of the payments list. */
    public record PaymentRow(Long id,
                             String receiptNo,
                             LocalDate receiptDate,
                             String familyName,
                             String familyCode,
                             BigDecimal amount,
                             String mode,
                             boolean voided) {
    }

    /** A receipt, as printed. */
    public record ReceiptView(Long id,
                              String receiptNo,
                              LocalDate receiptDate,
                              String churchName,
                              String churchAddress,
                              Long familyId,
                              String familyName,
                              String familyCode,
                              BigDecimal amount,
                              String mode,
                              String referenceNo,
                              String collector,
                              String remarks,
                              boolean voided,
                              String voidReason,
                              List<AllocationRow> settled) {
    }

    public record AllocationRow(String period, BigDecimal amount) {
    }

    // -------------------------------------------------------------------- reads

    /** What this family owes, oldest first -- the screen a collector works from. */
    @Transactional(readOnly = true)
    public FamilyDues duesFor(Long familyId) {
        Family family = family(familyId);

        List<DueRow> months = paymentDueRepository
                .findByFamilyIdAndDeletedFlagFalseOrderByDueYearAscDueMonthAsc(familyId).stream()
                .filter(PaymentDue::isOutstanding)
                .map(due -> new DueRow(due.getPeriodLabel(), due.getAmountDue(),
                        due.getAmountPaid(), due.getBalance(), due.getStatus()))
                .toList();

        return new FamilyDues(
                family.getId(),
                family.getFamilyCode(),
                family.getFamilyName(),
                family.getAnbiyam().getAnbiyamName(),
                headName(family.getHeadMemberId()),
                family.getMonthlyAmount(),
                outstandingFor(familyId),
                months);
    }

    @Transactional(readOnly = true)
    public List<PaymentRow> recentPayments(int limit) {
        return paymentRepository
                .findByChurchIdAndDeletedFlagFalseOrderByReceiptDateDesc(currentChurchId(),
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .getContent().stream()
                .map(PaymentService::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public com.church.app.dto.PageView<PaymentRow> list(int page, int size) {
        var found = paymentRepository.findByChurchIdAndDeletedFlagFalseOrderByReceiptDateDesc(
                currentChurchId(), org.springframework.data.domain.PageRequest.of(
                        Math.max(page, 1) - 1, size));
        return com.church.app.dto.PageView.of(found,
                found.getContent().stream().map(PaymentService::toRow).toList());
    }

    @Transactional(readOnly = true)
    public ReceiptView receipt(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .filter(p -> !p.isDeletedFlag())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        List<AllocationRow> settled = paymentAllocationRepository
                .findByPaymentIdOrderByPeriod(paymentId).stream()
                .map(a -> new AllocationRow(a.getPaymentDue().getPeriodLabel(), a.getAllocatedAmount()))
                .toList();

        Church church = payment.getChurch();
        return new ReceiptView(
                payment.getId(),
                payment.getReceiptNo(),
                payment.getReceiptDate(),
                church.getChurchName(),
                churchAddress(church),
                payment.getFamily().getId(),
                payment.getFamily().getFamilyName(),
                payment.getFamily().getFamilyCode(),
                payment.getAmount(),
                payment.getPaymentMode().getLabel(),
                payment.getReferenceNo(),
                payment.getReceivedBy() == null ? null : payment.getReceivedBy().getDisplayName(),
                payment.getRemarks(),
                payment.getStatus() == PaymentStatus.VOID,
                payment.getVoidReason(),
                settled);
    }

    /** A family a collector can pick, with what it owes. Deliberately not the full dues. */
    public record FamilyChoice(Long familyId,
                               String familyCode,
                               String familyName,
                               String headName,
                               BigDecimal outstanding) {
    }

    /**
     * Families a collector can choose from, one page at a time.
     *
     * <p>Every arrears figure comes from a single GROUP BY rather than a query per family.
     * The first version asked the database once per household -- fine against three
     * families, twelve hundred round trips against six hundred, on the screen used most.
     */
    @Transactional(readOnly = true)
    public com.church.app.dto.PageView<FamilyChoice> familiesWithDues(String search, int page, int size) {
        Long churchId = currentChurchId();
        String term = search == null || search.isBlank() ? null : search.trim().toLowerCase();

        var found = familyRepository.search(churchId, term, term == null ? null : term, null,
                org.springframework.data.domain.PageRequest.of(Math.max(page, 1) - 1, size));

        java.util.Map<Long, BigDecimal> arrears = paymentDueRepository.findArrearsByChurch(churchId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        PaymentDueRepository.FamilyArrears::getFamilyId,
                        PaymentDueRepository.FamilyArrears::getPendingAmount));

        java.util.Map<Long, String> heads = namesOf(found.getContent().stream()
                .map(Family::getHeadMemberId)
                .filter(java.util.Objects::nonNull)
                .toList());

        return com.church.app.dto.PageView.of(found, found.getContent().stream()
                .map(family -> new FamilyChoice(
                        family.getId(),
                        family.getFamilyCode(),
                        family.getFamilyName(),
                        heads.get(family.getHeadMemberId()),
                        arrears.getOrDefault(family.getId(), BigDecimal.ZERO)))
                .toList());
    }

    /** A page's worth of member ids to names, in one query. */
    private java.util.Map<Long, String> namesOf(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return java.util.Map.of();
        }
        return memberRepository.findAllById(memberIds).stream()
                .filter(member -> !member.isDeletedFlag())
                .collect(java.util.stream.Collectors.toMap(
                        member -> member.getId(), member -> member.getDisplayName()));
    }

    private static PaymentRow toRow(Payment payment) {
        return new PaymentRow(
                payment.getId(),
                payment.getReceiptNo(),
                payment.getReceiptDate(),
                payment.getFamily().getFamilyName(),
                payment.getFamily().getFamilyCode(),
                payment.getAmount(),
                payment.getPaymentMode().getLabel(),
                payment.getStatus() == PaymentStatus.VOID);
    }

    private String headName(Long headMemberId) {
        if (headMemberId == null) {
            return null;
        }
        return memberRepository.findById(headMemberId)
                .map(member -> member.getDisplayName())
                .orElse(null);
    }

    private static String churchAddress(Church church) {
        StringBuilder address = new StringBuilder();
        for (String part : new String[]{church.getAddressLine1(), church.getCity(), church.getPincode()}) {
            if (part != null && !part.isBlank()) {
                if (!address.isEmpty()) {
                    address.append(", ");
                }
                address.append(part);
            }
        }
        return address.toString();
    }

    // ------------------------------------------------------------------ collect

    /**
     * Records money received and settles the oldest months first.
     *
     * <p>Anything left once the arrears are clear runs forward: the next months' dues are
     * generated at the family's current rate and settled, up to {@link #MAX_ADVANCE_MONTHS}.
     * That was the choice made over holding the surplus as an unallocated credit.
     */
    @Transactional
    public CollectionResult collect(PaymentForm form) {
        Long churchId = currentChurchId();
        Family family = family(form.getFamilyId());
        BigDecimal amount = form.getAmount().setScale(2, java.math.RoundingMode.HALF_UP);

        if (amount.signum() <= 0) {
            throw new BusinessException("The amount must be more than zero.");
        }

        Payment payment = new Payment();
        payment.setUuid(UUID.randomUUID().toString());
        payment.setChurch(church(churchId));
        payment.setFamily(family);
        payment.setAmount(amount);
        payment.setPaymentMode(form.getPaymentMode());
        payment.setReferenceNo(trimToNull(form.getReferenceNo()));
        payment.setRemarks(trimToNull(form.getRemarks()));
        payment.setReceiptDate(receiptDate(form.getReceiptDate()));
        payment.setStatus(PaymentStatus.ACTIVE);
        payment.setRecordStatus("ACTIVE");
        payment.setReceivedBy(collector());

        // Numbered inside this transaction: roll back and the number is never consumed.
        String receiptNo = nextReceiptNo(churchId, payment.getReceiptDate());
        payment.setReceiptNo(receiptNo);
        payment.setReceiptYear((short) payment.getReceiptDate().getYear());

        Payment saved = paymentRepository.save(payment);

        BigDecimal arrearsBefore = outstandingFor(family.getId());
        List<String> settled = new ArrayList<>();
        BigDecimal remaining = allocate(saved, outstandingDues(family.getId()), amount, settled);

        BigDecimal paidForward = BigDecimal.ZERO;
        if (remaining.signum() > 0) {
            paidForward = remaining;
            remaining = allocate(saved, generateAdvanceDues(family, remaining), remaining, settled);
        }

        // Whatever could not be placed even after running forward. Only reachable by a
        // family paying more than two years ahead.
        if (remaining.signum() > 0) {
            log.warn("Receipt {} has {} that could not be allocated", receiptNo, remaining);
            paidForward = paidForward.subtract(remaining);
        }

        saved.setAllocatedAmount(amount.subtract(remaining));
        paymentRepository.save(saved);

        audit(AuditEventType.RECORD_CREATED, Operation.ADD, saved,
                "Collected " + amount + " from " + family.getFamilyName());

        return new CollectionResult(saved.getId(), receiptNo, amount,
                arrearsBefore.min(amount), paidForward, settled);
    }

    // --------------------------------------------------------------------- void

    /**
     * Cancels a receipt and puts the dues back as they were.
     *
     * <p>Not a delete. The row stays, the number stays consumed, and the reason is on
     * record -- so a slip already in a family's hands can always be checked against it.
     */
    @Transactional
    public void voidPayment(Long paymentId, String reason) {
        if (trimToNull(reason) == null) {
            throw new BusinessException("A reason is required to void a receipt.");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .filter(p -> !p.isDeletedFlag())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (payment.getStatus() == PaymentStatus.VOID) {
            throw new BusinessException("Receipt " + payment.getReceiptNo() + " is already void.");
        }

        // Give every settled month its money back before the payment stops counting.
        paymentAllocationRepository.findByPaymentIdOrderByPeriod(paymentId).forEach(allocation -> {
            PaymentDue due = allocation.getPaymentDue();
            due.setAmountPaid(due.getAmountPaid().subtract(allocation.getAllocatedAmount()));
            if (due.getAmountPaid().signum() < 0) {
                due.setAmountPaid(BigDecimal.ZERO);
            }
            due.recalculateStatus();
            paymentDueRepository.save(due);
        });
        paymentAllocationRepository.deleteByPaymentId(paymentId);

        AppUserPrincipal principal = CurrentUser.principalOrNull();
        payment.setStatus(PaymentStatus.VOID);
        payment.setVoidReason(reason.trim());
        payment.setVoidedDate(LocalDateTime.now());
        payment.setVoidedUser(principal == null ? "SYSTEM" : principal.getUsername());
        payment.setAllocatedAmount(BigDecimal.ZERO);
        paymentRepository.save(payment);

        audit(AuditEventType.RECORD_DELETED, Operation.DELETE, payment,
                "Voided receipt " + payment.getReceiptNo() + ": " + reason.trim());
    }

    /**
     * Voids a receipt and records the correct one in its place, cross-referenced both ways.
     *
     * <p>This is how a wrong amount is corrected. Editing the original would leave the
     * parish's books disagreeing with a printed slip the family is holding.
     */
    @Transactional
    public CollectionResult voidAndReissue(Long paymentId, String reason, PaymentForm corrected) {
        Payment original = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        String originalNo = original.getReceiptNo();

        voidPayment(paymentId, reason + " (replaced)");

        corrected.setFamilyId(original.getFamily().getId());
        corrected.setRemarks(join(corrected.getRemarks(), "Replaces receipt " + originalNo));
        CollectionResult result = collect(corrected);

        Payment voided = paymentRepository.findById(paymentId).orElseThrow();
        voided.setVoidReason(reason.trim() + " (replaced by " + result.receiptNo() + ")");
        paymentRepository.save(voided);

        return result;
    }

    /** Remarks and reference only. The amount is corrected by voiding and reissuing. */
    @Transactional
    public void updateDetails(Long paymentId, String referenceNo, String remarks) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        payment.setReferenceNo(trimToNull(referenceNo));
        payment.setRemarks(trimToNull(remarks));
        paymentRepository.save(payment);

        audit(AuditEventType.RECORD_UPDATED, Operation.EDIT, payment,
                "Updated details on receipt " + payment.getReceiptNo());
    }

    // ------------------------------------------------------------- due generation

    /**
     * Creates one month's dues for every family that has started paying.
     *
     * <p>Idempotent: the unique key on {@code family_id, due_year, due_month} means a
     * second run skips rather than charging anyone twice, so a double-click is harmless.
     */
    @Transactional
    public DueGenerationResult generateDues(YearMonth period) {
        Long churchId = currentChurchId();
        LocalDate firstOfMonth = period.atDay(1);

        int created = 0;
        int skipped = 0;

        for (Family family : familyRepository.findByChurchIdAndDeletedFlagFalse(churchId)) {
            if (family.getDuesStartDate() == null || family.getDuesStartDate().isAfter(firstOfMonth)) {
                skipped++;
                continue;
            }
            if (paymentDueRepository.existsByFamilyIdAndDueYearAndDueMonth(
                    family.getId(), (short) period.getYear(), (byte) period.getMonthValue())) {
                skipped++;
                continue;
            }
            createDue(family, period);
            created++;
        }

        auditService.event(AuditEventType.RECORD_CREATED)
                .inChurch(churchId)
                .permission(Resource.PAYMENT, Operation.ADD)
                .describe("Generated %d dues for %s (%d skipped)".formatted(created, period, skipped))
                .save();

        return new DueGenerationResult(created, skipped, period);
    }

    /**
     * Applies a new monthly amount to the months not yet paid for.
     *
     * <p>Only {@link DueStatus#PENDING} months from the current one onward: a month that
     * has been paid or part-paid was settled against a figure the family already has a
     * receipt for, and rewriting it would make their history disagree with their paper.
     *
     * @return how many future dues were changed
     */
    @Transactional
    public int applyMonthlyAmountChange(Long familyId, BigDecimal newAmount) {
        Family family = family(familyId);
        BigDecimal amount = newAmount.setScale(2, java.math.RoundingMode.HALF_UP);

        if (amount.compareTo(paymentProperties.getMinMonthlyAmount()) < 0) {
            throw new BusinessException("The monthly amount may not be below "
                    + paymentProperties.getMinMonthlyAmount() + ".");
        }

        YearMonth thisMonth = YearMonth.now();
        int changed = 0;

        for (PaymentDue due : paymentDueRepository
                .findByFamilyIdAndDeletedFlagFalseOrderByDueYearAscDueMonthAsc(familyId)) {
            YearMonth period = YearMonth.of(due.getDueYear(), due.getDueMonth());
            boolean future = !period.isBefore(thisMonth);

            if (future && due.getStatus() == DueStatus.PENDING
                    && due.getAmountDue().compareTo(amount) != 0) {
                due.setAmountDue(amount);
                due.recalculateStatus();
                paymentDueRepository.save(due);
                changed++;
            }
        }

        family.setMonthlyAmount(amount);
        familyRepository.save(family);

        auditService.event(AuditEventType.RECORD_UPDATED)
                .inChurch(family.getChurch().getId())
                .on("Family", family.getId(), family.getFamilyName())
                .permission(Resource.PAYMENT, Operation.EDIT)
                .describe("Monthly amount set to %s; %d future dues updated".formatted(amount, changed))
                .save();

        return changed;
    }

    // ------------------------------------------------------- opening balances

    /** A family and whatever arrears it brought in, for the cutover screen. */
    public record OpeningBalanceRow(Long familyId,
                                    String familyCode,
                                    String familyName,
                                    BigDecimal balance,
                                    BigDecimal settled,
                                    boolean locked,
                                    String period) {
    }

    /**
     * The cutover screen, a page at a time.
     *
     * <p>Every opening balance in the parish is fetched in one query and matched up in
     * memory, rather than a lookup per family. This screen is used once, but it is used
     * on the day the parish goes live -- a bad day for it to take a minute to open.
     */
    @Transactional(readOnly = true)
    public com.church.app.dto.PageView<OpeningBalanceRow> openingBalances(int page, int size) {
        Long churchId = currentChurchId();
        var found = familyRepository.search(churchId, null, null, null,
                org.springframework.data.domain.PageRequest.of(Math.max(page, 1) - 1, size));

        java.util.Map<Long, PaymentDue> existing = paymentDueRepository
                .findByChurchIdAndDueTypeAndDeletedFlagFalse(churchId, DueType.OPENING_BALANCE)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        due -> due.getFamily().getId(), due -> due, (first, second) -> first));

        return com.church.app.dto.PageView.of(found, found.getContent().stream()
                .map(family -> {
                    PaymentDue opening = existing.get(family.getId());
                    return new OpeningBalanceRow(
                            family.getId(),
                            family.getFamilyCode(),
                            family.getFamilyName(),
                            opening == null ? BigDecimal.ZERO : opening.getAmountDue(),
                            opening == null ? BigDecimal.ZERO : opening.getAmountPaid(),
                            opening != null && opening.getAmountPaid().signum() > 0,
                            openingBalanceSlot(family).toString());
                })
                .toList());
    }

    /**
     * Records what a family owed before the parish started using this application.
     *
     * <p>Dated the month before their {@code dues_start_date}: a slot no generated month
     * can occupy, and one that sorts first, so arrears carried forward are settled before
     * anything current.
     *
     * <p>Locked once money has landed on it. Rewriting a figure a receipt was already
     * written against would leave the parish's books disagreeing with the family's paper.
     */
    @Transactional
    public void setOpeningBalance(Long familyId, BigDecimal amount) {
        Family family = family(familyId);
        BigDecimal value = amount == null
                ? BigDecimal.ZERO
                : amount.setScale(2, java.math.RoundingMode.HALF_UP);

        if (value.signum() < 0) {
            // A family in credit is not a negative debt: it is money the parish already
            // holds, and it is recorded as a receipt instead.
            throw new BusinessException(
                    "An opening balance cannot be negative. Record a family in credit as a payment.");
        }

        PaymentDue existing = openingBalanceRow(familyId).orElse(null);

        if (existing != null && existing.getAmountPaid().signum() > 0) {
            throw new BusinessException(
                    "Money has already been received against this opening balance, so it can no "
                            + "longer be changed. Void the receipt first if it was wrong.");
        }

        if (value.signum() == 0) {
            if (existing != null) {
                existing.setDeletedFlag(true);
                paymentDueRepository.save(existing);
            }
            return;
        }

        YearMonth slot = openingBalanceSlot(family);
        PaymentDue due = existing == null ? new PaymentDue() : existing;

        if (existing == null) {
            due.setUuid(UUID.randomUUID().toString());
            due.setChurch(family.getChurch());
            due.setFamily(family);
            due.setDueYear((short) slot.getYear());
            due.setDueMonth((byte) slot.getMonthValue());
            due.setDueDate(slot.atEndOfMonth());
            due.setDueType(DueType.OPENING_BALANCE);
            due.setAmountPaid(BigDecimal.ZERO);
            due.setRecordStatus("ACTIVE");
        }

        due.setAmountDue(value);
        due.setRemarks("Arrears carried forward at cutover");
        due.setDeletedFlag(false);
        due.recalculateStatus();
        paymentDueRepository.save(due);

        auditService.event(AuditEventType.RECORD_UPDATED)
                .inChurch(family.getChurch().getId())
                .on("Family", family.getId(), family.getFamilyName())
                .permission(Resource.PAYMENT, Operation.EDIT)
                .describe("Opening balance set to " + value)
                .save();
    }

    private java.util.Optional<PaymentDue> openingBalanceRow(Long familyId) {
        return paymentDueRepository
                .findByFamilyIdAndDeletedFlagFalseOrderByDueYearAscDueMonthAsc(familyId).stream()
                .filter(due -> due.getDueType() == DueType.OPENING_BALANCE)
                .findFirst();
    }

    /**
     * The month before this family starts paying -- guaranteed free, because nothing is
     * ever generated before {@code dues_start_date}.
     */
    private static YearMonth openingBalanceSlot(Family family) {
        LocalDate start = family.getDuesStartDate() == null
                ? LocalDate.now().withDayOfMonth(1)
                : family.getDuesStartDate();
        return YearMonth.from(start).minusMonths(1);
    }

    // ---------------------------------------------------------------- internals

    /**
     * Places money against dues, oldest first, and records what settled what.
     *
     * @return whatever is left over
     */
    private BigDecimal allocate(Payment payment, List<PaymentDue> dues, BigDecimal available,
                                List<String> settledPeriods) {
        BigDecimal remaining = available;

        for (PaymentDue due : dues) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal owed = due.getBalance();
            if (owed.signum() <= 0) {
                continue;
            }

            BigDecimal applied = owed.min(remaining);
            due.setAmountPaid(due.getAmountPaid().add(applied));
            due.recalculateStatus();
            paymentDueRepository.save(due);

            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setUuid(UUID.randomUUID().toString());
            allocation.setChurch(payment.getChurch());
            allocation.setPayment(payment);
            allocation.setPaymentDue(due);
            allocation.setAllocatedAmount(applied);
            paymentAllocationRepository.save(allocation);

            settledPeriods.add(due.getPeriodLabel());
            remaining = remaining.subtract(applied);
        }
        return remaining;
    }

    private List<PaymentDue> outstandingDues(Long familyId) {
        return paymentDueRepository.findOutstandingByFamily(familyId);
    }

    private BigDecimal outstandingFor(Long familyId) {
        return outstandingDues(familyId).stream()
                .map(PaymentDue::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Creates the months a forward payment will settle.
     *
     * <p>Capped, so a mistyped amount cannot commit a family to dues stretching decades
     * into the future.
     */
    private List<PaymentDue> generateAdvanceDues(Family family, BigDecimal available) {
        BigDecimal monthly = family.getMonthlyAmount();
        if (monthly == null || monthly.signum() <= 0) {
            return List.of();
        }

        YearMonth period = lastDuePeriod(family.getId()).plusMonths(1);
        List<PaymentDue> created = new ArrayList<>();
        BigDecimal remaining = available;

        for (int month = 0; month < MAX_ADVANCE_MONTHS && remaining.signum() > 0; month++) {
            if (!paymentDueRepository.existsByFamilyIdAndDueYearAndDueMonth(
                    family.getId(), (short) period.getYear(), (byte) period.getMonthValue())) {
                created.add(createDue(family, period));
            }
            remaining = remaining.subtract(monthly);
            period = period.plusMonths(1);
        }
        return created;
    }

    private YearMonth lastDuePeriod(Long familyId) {
        return paymentDueRepository
                .findByFamilyIdAndDeletedFlagFalseOrderByDueYearAscDueMonthAsc(familyId).stream()
                .map(due -> YearMonth.of(due.getDueYear(), due.getDueMonth()))
                .max(YearMonth::compareTo)
                .orElse(YearMonth.now().minusMonths(1));
    }

    private PaymentDue createDue(Family family, YearMonth period) {
        PaymentDue due = new PaymentDue();
        due.setUuid(UUID.randomUUID().toString());
        due.setChurch(family.getChurch());
        due.setFamily(family);
        due.setDueYear((short) period.getYear());
        due.setDueMonth((byte) period.getMonthValue());
        due.setDueDate(period.atDay(1));
        due.setDueType(DueType.MONTHLY);
        due.setAmountDue(family.getMonthlyAmount());
        due.setAmountPaid(BigDecimal.ZERO);
        due.setStatus(DueStatus.PENDING);
        due.setRecordStatus("ACTIVE");
        return paymentDueRepository.save(due);
    }

    /**
     * Issues the next number for this parish and year.
     *
     * <p>The row is locked for the rest of the transaction, so two collectors saving at
     * the same moment queue rather than both taking the same number. At parish scale that
     * costs nothing; a duplicated receipt number would cost a great deal.
     */
    private String nextReceiptNo(Long churchId, LocalDate receiptDate) {
        short year = (short) receiptDate.getYear();

        ReceiptSequence sequence = receiptSequenceRepository.findForUpdate(churchId, year)
                .orElseGet(() -> {
                    ReceiptSequence fresh = new ReceiptSequence();
                    fresh.setChurch(church(churchId));
                    fresh.setSequenceYear(year);
                    fresh.setPrefix(paymentProperties.getReceiptPrefix());
                    fresh.setLastNumber(0);
                    return receiptSequenceRepository.save(fresh);
                });

        String receiptNo = sequence.nextReceiptNo();
        receiptSequenceRepository.save(sequence);
        return receiptNo;
    }

    /**
     * Today unless an administrator says otherwise, and never in the future.
     *
     * <p>A volunteer does not choose what day they took cash -- that is the field that
     * lets a shortfall be moved into another week. A future date is refused for everyone:
     * there is no honest reason to write one.
     */
    private LocalDate receiptDate(LocalDate requested) {
        LocalDate today = LocalDate.now();
        if (requested == null || requested.equals(today)) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new BusinessException("A receipt cannot be dated in the future.");
        }
        if (!mayBackdate()) {
            throw new BusinessException("Only an administrator may date a receipt in the past.");
        }
        return requested;
    }

    private boolean mayBackdate() {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        if (principal == null || principal.isPlatformUser()) {
            return true;
        }
        return principal.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals("PERM_PAYMENT_EDIT"));
    }

    /** The signed-in parish member, so the register says who took the money. */
    private com.church.app.entity.Member collector() {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        if (principal == null || principal.isPlatformUser()) {
            return null;
        }
        return memberRepository.findById(principal.getUserId()).orElse(null);
    }

    private Family family(Long familyId) {
        return familyRepository.findById(familyId)
                .filter(family -> !family.isDeletedFlag())
                .orElseThrow(() -> new BusinessException("That family is not in this parish."));
    }

    private Church church(Long churchId) {
        return churchRepository.findById(churchId)
                .orElseThrow(() -> new ResourceNotFoundException("Church", churchId));
    }

    private Long currentChurchId() {
        return TenantContext.currentChurchId().orElseThrow(() -> new BusinessException(
                "No parish is selected, so there is nothing to collect against."));
    }

    private void audit(AuditEventType eventType, Operation operation, Payment payment, String description) {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        AuditService.Entry entry = auditService.event(eventType)
                .on("Payment", payment.getId(), payment.getReceiptNo())
                .inChurch(payment.getChurch().getId())
                .permission(Resource.PAYMENT, operation)
                .describe(description);

        if (principal != null && principal.isPlatformUser()) {
            entry.actorSaasUser(principal.getUserId(), principal.getDisplayName());
        } else if (principal != null) {
            entry.actorMember(principal.getUserId(), principal.getDisplayName(), principal.getChurchId());
        }
        entry.save();
    }

    private static String join(String first, String second) {
        return first == null || first.isBlank() ? second : first + " · " + second;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
