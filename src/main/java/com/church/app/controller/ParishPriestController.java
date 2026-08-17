package com.church.app.controller;

import com.church.app.dto.ParishPriestForm;
import com.church.app.entity.ClergyRole;
import com.church.app.exception.BusinessException;
import com.church.app.service.ParishPriestService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Clergy appointments for the parish in scope.
 *
 * <p>Only the platform roles may appoint: parish staff see who serves them but cannot
 * change it, an appointment being a diocese-level act. That is enforced here, not merely
 * by hiding buttons.
 */
@Controller
@RequestMapping("/parish-priest")
public class ParishPriestController {

    private final ParishPriestService parishPriestService;

    public ParishPriestController(ParishPriestService parishPriestService) {
        this.parishPriestService = parishPriestService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PARISH_PRIEST_VIEW')")
    public String list(Model model) {
        model.addAttribute("postings", parishPriestService.history());
        model.addAttribute("noParishPriest", parishPriestService.hasNoParishPriest());
        return "parish-priest/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PERM_PARISH_PRIEST_ADD')")
    public String addForm(Model model) {
        model.addAttribute("form", new ParishPriestForm());
        return withRoles(model);
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('PERM_PARISH_PRIEST_ADD')")
    public String create(@Valid @ModelAttribute("form") ParishPriestForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return withRoles(model);
        }
        try {
            parishPriestService.appoint(form);
        } catch (BusinessException e) {
            binding.rejectValue("fromDate", "invalid", e.getMessage());
            return withRoles(model);
        }
        redirect.addFlashAttribute("flash", "Appointment recorded.");
        return "redirect:/parish-priest";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PERM_PARISH_PRIEST_EDIT')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", parishPriestService.formFor(id));
        return withRoles(model);
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PERM_PARISH_PRIEST_EDIT')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ParishPriestForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return withRoles(model);
        }
        try {
            parishPriestService.update(id, form);
        } catch (BusinessException e) {
            binding.rejectValue("fromDate", "invalid", e.getMessage());
            return withRoles(model);
        }
        redirect.addFlashAttribute("flash", "Appointment updated.");
        return "redirect:/parish-priest";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('PERM_PARISH_PRIEST_DELETE')")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            parishPriestService.delete(id);
            redirect.addFlashAttribute("flash", "Appointment removed.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/parish-priest";
    }

    private String withRoles(Model model) {
        model.addAttribute("clergyRoles", ClergyRole.values());
        return "parish-priest/form";
    }
}
