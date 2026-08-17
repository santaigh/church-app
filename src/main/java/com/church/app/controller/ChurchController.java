package com.church.app.controller;

import com.church.app.dto.ChurchForm;
import com.church.app.exception.BusinessException;
import com.church.app.service.ChurchService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Creating and maintaining the parishes themselves. Platform staff only.
 *
 * <p>Sits under {@code /saas/**}, so the platform security chain gates it before these
 * checks are even reached — a parish account cannot get here at all.
 */
@Controller
@RequestMapping("/saas/churches")
public class ChurchController {

    private final ChurchService churchService;

    public ChurchController(ChurchService churchService) {
        this.churchService = churchService;
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PERM_CHURCH_ADD')")
    public String addForm(Model model) {
        model.addAttribute("form", new ChurchForm());
        return "church/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('PERM_CHURCH_ADD')")
    public String create(@Valid @ModelAttribute("form") ChurchForm form,
                         BindingResult binding,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "church/form";
        }
        try {
            var church = churchService.create(form);
            redirect.addFlashAttribute("flash",
                    church.getChurchName() + " created."
                            + (form.getAdministrator().isEmpty()
                            ? " No administrator was added, so nobody can sign into it yet."
                            : " Its first administrator can sign in with the default password."));
            return "redirect:/saas/dashboard?church=" + church.getId();
        } catch (BusinessException e) {
            binding.rejectValue("churchName", "invalid", e.getMessage());
            return "church/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PERM_CHURCH_EDIT')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", churchService.formFor(id));
        model.addAttribute("substations", churchService.substationsOf(id));
        return "church/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PERM_CHURCH_EDIT')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ChurchForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("substations", churchService.substationsOf(id));
            return "church/form";
        }
        try {
            churchService.update(id, form);
            redirect.addFlashAttribute("flash", "Parish updated.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/saas/dashboard?church=" + id;
    }

    /** The crest shown in the header. Files land as {@code <church_id>.png}. */
    @PostMapping("/{id}/logo")
    @PreAuthorize("hasAuthority('PERM_CHURCH_EDIT')")
    public String uploadLogo(@PathVariable Long id,
                             @RequestParam("logo") MultipartFile logo,
                             RedirectAttributes redirect) {
        try {
            churchService.uploadLogo(id, logo);
            redirect.addFlashAttribute("flash", "Logo updated.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/saas/churches/" + id + "/edit";
    }

    // ------------------------------------------------------------- substations

    @PostMapping("/{id}/substations")
    @PreAuthorize("hasAuthority('PERM_CHURCH_EDIT')")
    public String addSubstation(@PathVariable Long id,
                                @RequestParam("name") String name,
                                @RequestParam(name = "location", required = false) String location,
                                @RequestParam(name = "address", required = false) String address,
                                RedirectAttributes redirect) {
        try {
            churchService.addSubstation(id, name, location, address);
            redirect.addFlashAttribute("flash", "Substation added.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/saas/churches/" + id + "/edit";
    }

    @PostMapping("/{id}/substations/{substationId}/remove")
    @PreAuthorize("hasAuthority('PERM_CHURCH_EDIT')")
    public String removeSubstation(@PathVariable Long id,
                                   @PathVariable Long substationId,
                                   RedirectAttributes redirect) {
        try {
            churchService.removeSubstation(substationId);
            redirect.addFlashAttribute("flash", "Substation removed.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/saas/churches/" + id + "/edit";
    }

    // ---------------------------------------------------------- remove/restore

    /**
     * Removing a parish hides its entire register, so the name must be typed rather than
     * a button clicked. Super admin only — SaaSAdmin holds no DELETE anywhere.
     */
    @PostMapping("/{id}/remove")
    @PreAuthorize("hasAuthority('PERM_CHURCH_DELETE')")
    public String remove(@PathVariable Long id,
                         @RequestParam("confirmName") String confirmName,
                         RedirectAttributes redirect) {
        try {
            churchService.remove(id, confirmName);
            redirect.addFlashAttribute("flash",
                    "Parish removed. Its members, families and receipts are retained and "
                            + "come back if it is restored.");
            return "redirect:/saas/dashboard";
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/saas/churches/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('PERM_CHURCH_DELETE')")
    public String restore(@PathVariable Long id, RedirectAttributes redirect) {
        churchService.restore(id);
        redirect.addFlashAttribute("flash", "Parish restored, with everything it held.");
        return "redirect:/saas/dashboard?church=" + id;
    }
}
