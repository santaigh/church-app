package com.church.app.controller;

import com.church.app.dto.PageView;
import com.church.app.dto.PaymentForm;
import com.church.app.entity.PaymentMode;
import com.church.app.exception.BusinessException;
import com.church.app.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

/**
 * Collecting money, and the receipts that come out of it.
 *
 * <p>The permissions map to who does what in a parish: a volunteer collects and prints,
 * an administrator corrects a reference number, and only the super admin can cancel a
 * receipt.
 */
@Controller
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public String list(@RequestParam(name = "page", defaultValue = "1") int page,
                       @RequestParam(name = "size", defaultValue = "50") int size,
                       Model model) {
        model.addAttribute("paged", paymentService.list(page,
                PageView.SIZES.contains(size) ? size : PageView.DEFAULT_SIZE));
        model.addAttribute("params", "");
        return "payment/list";
    }

    // ------------------------------------------------------------------ collect

    /** Choosing a family. Its own step, because it is the one done standing up. */
    @GetMapping("/collect")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADD')")
    public String chooseFamily(@RequestParam(name = "q", required = false) String q,
                               @RequestParam(name = "page", defaultValue = "1") int page,
                               @RequestParam(name = "size", defaultValue = "50") int size,
                               Model model) {
        model.addAttribute("paged", paymentService.familiesWithDues(q, page,
                PageView.SIZES.contains(size) ? size : PageView.DEFAULT_SIZE));
        model.addAttribute("q", q);
        model.addAttribute("params", QueryParams.of().add("q", q).toString());
        return "payment/choose-family";
    }

    @GetMapping("/collect/{familyId}")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADD')")
    public String collectForm(@PathVariable Long familyId, Model model) {
        PaymentService.FamilyDues dues = paymentService.duesFor(familyId);

        PaymentForm form = new PaymentForm();
        form.setFamilyId(familyId);
        // Pre-filled with what is owed, which is what is handed over most of the time.
        form.setAmount(dues.outstanding().signum() > 0 ? dues.outstanding() : dues.monthlyAmount());

        model.addAttribute("form", form);
        model.addAttribute("dues", dues);
        model.addAttribute("modes", PaymentMode.values());
        return "payment/collect";
    }

    @PostMapping("/collect")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADD')")
    public String collect(@Valid @ModelAttribute("form") PaymentForm form,
                          BindingResult binding,
                          Model model,
                          RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return redisplayCollect(form, model);
        }
        try {
            PaymentService.CollectionResult result = paymentService.collect(form);
            redirect.addFlashAttribute("flash",
                    "Receipt " + result.receiptNo() + " recorded.");
            return "redirect:/payments/" + result.paymentId();
        } catch (BusinessException e) {
            binding.rejectValue("amount", "invalid", e.getMessage());
            return redisplayCollect(form, model);
        }
    }

    // ------------------------------------------------------------------ receipt

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public String receipt(@PathVariable Long id, Model model) {
        model.addAttribute("receipt", paymentService.receipt(id));
        return "payment/receipt";
    }

    /**
     * The slip itself: no navigation, no colour, 58mm wide.
     *
     * <p>Its own page rather than a print stylesheet over the receipt screen, so what
     * goes to the printer is exactly what is on it and nothing else.
     */
    @GetMapping("/{id}/print")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
    public String print(@PathVariable Long id, Model model) {
        model.addAttribute("receipt", paymentService.receipt(id));
        return "payment/print";
    }

    // --------------------------------------------------------------------- void

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_DELETE')")
    public String voidReceipt(@PathVariable Long id,
                              @RequestParam("reason") String reason,
                              RedirectAttributes redirect) {
        try {
            paymentService.voidPayment(id, reason);
            redirect.addFlashAttribute("flash", "Receipt voided.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/payments/" + id;
    }

    @GetMapping("/{id}/reissue")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_DELETE')")
    public String reissueForm(@PathVariable Long id, Model model) {
        PaymentService.ReceiptView original = paymentService.receipt(id);
        PaymentForm form = new PaymentForm();
        form.setAmount(original.amount());

        model.addAttribute("original", original);
        model.addAttribute("form", form);
        model.addAttribute("modes", PaymentMode.values());
        return "payment/reissue";
    }

    @PostMapping("/{id}/reissue")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_DELETE')")
    public String reissue(@PathVariable Long id,
                          @RequestParam("reason") String reason,
                          @Valid @ModelAttribute("form") PaymentForm form,
                          BindingResult binding,
                          Model model,
                          RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("original", paymentService.receipt(id));
            model.addAttribute("modes", PaymentMode.values());
            return "payment/reissue";
        }
        try {
            PaymentService.CollectionResult result = paymentService.voidAndReissue(id, reason, form);
            redirect.addFlashAttribute("flash",
                    "Receipt reissued as " + result.receiptNo() + ".");
            return "redirect:/payments/" + result.paymentId();
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/payments/" + id;
        }
    }

    // ----------------------------------------------------------------- due runs

    @GetMapping("/dues")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADD')")
    public String duesForm(Model model) {
        model.addAttribute("period", YearMonth.now().toString());
        return "payment/dues";
    }

    @PostMapping("/dues")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADD')")
    public String generateDues(@RequestParam("period") String period,
                               RedirectAttributes redirect) {
        try {
            PaymentService.DueGenerationResult result =
                    paymentService.generateDues(YearMonth.parse(period));
            redirect.addFlashAttribute("flash",
                    "%d dues created for %s. %d skipped -- they already existed or the family has not started paying."
                            .formatted(result.created(), result.period(), result.skipped()));
        } catch (java.time.format.DateTimeParseException e) {
            redirect.addFlashAttribute("flashError", "Choose a month.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/payments/dues";
    }

    // ------------------------------------------------------- opening balances

    /**
     * The cutover screen: what each family owed before the parish started using this.
     *
     * <p>Restricted to an administrator. Declaring what every family owes is not a
     * volunteer's decision.
     */
    @GetMapping("/opening-balance")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_EDIT')")
    public String openingBalances(@RequestParam(name = "page", defaultValue = "1") int page,
                                  @RequestParam(name = "size", defaultValue = "50") int size,
                                  Model model) {
        model.addAttribute("paged", paymentService.openingBalances(page,
                PageView.SIZES.contains(size) ? size : PageView.DEFAULT_SIZE));
        model.addAttribute("params", "");
        return "payment/opening-balance";
    }

    @PostMapping("/opening-balance")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_EDIT')")
    public String saveOpeningBalances(@RequestParam java.util.Map<String, String> amounts,
                                      RedirectAttributes redirect) {
        int saved = 0;
        java.util.List<String> refused = new java.util.ArrayList<>();

        for (var entry : amounts.entrySet()) {
            if (!entry.getKey().startsWith("balance-")) {
                continue;
            }
            Long familyId = Long.valueOf(entry.getKey().substring("balance-".length()));
            String raw = entry.getValue() == null ? "" : entry.getValue().trim();

            try {
                paymentService.setOpeningBalance(familyId,
                        raw.isEmpty() ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(raw));
                saved++;
            } catch (BusinessException | NumberFormatException e) {
                // One bad row must not discard the other five hundred.
                refused.add(familyId + ": " + e.getMessage());
            }
        }

        redirect.addFlashAttribute("flash", saved + " opening balances saved.");
        if (!refused.isEmpty()) {
            redirect.addFlashAttribute("flashError", String.join(" · ", refused));
        }
        return "redirect:/payments/opening-balance";
    }

    private String redisplayCollect(PaymentForm form, Model model) {
        model.addAttribute("dues", paymentService.duesFor(form.getFamilyId()));
        model.addAttribute("modes", PaymentMode.values());
        return "payment/collect";
    }
}
