package com.church.app.controller;

import com.church.app.dto.AnbiyamForm;
import com.church.app.exception.BusinessException;
import com.church.app.service.AnbiyamService;
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
 * The anbiyam of the current parish.
 *
 * <p>Every method carries its own {@code @PreAuthorize}. Hiding a menu item is
 * presentation; this is the door. The permission rows have been loaded as authorities at
 * sign-in since the beginning and, until this module, were never consulted.
 */
@Controller
@RequestMapping("/anbiyam")
public class AnbiyamController {

    private final AnbiyamService anbiyamService;

    public AnbiyamController(AnbiyamService anbiyamService) {
        this.anbiyamService = anbiyamService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ANBIYAM_VIEW')")
    public String list(Model model) {
        model.addAttribute("rows", anbiyamService.list());
        return "anbiyam/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PERM_ANBIYAM_ADD')")
    public String addForm(Model model) {
        model.addAttribute("form", new AnbiyamForm());
        model.addAttribute("animators", anbiyamService.animatorOptions(null));
        return "anbiyam/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('PERM_ANBIYAM_ADD')")
    public String create(@Valid @ModelAttribute("form") AnbiyamForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return redisplay(model, null);
        }
        try {
            anbiyamService.create(form);
        } catch (BusinessException e) {
            binding.rejectValue("anbiyamName", "invalid", e.getMessage());
            return redisplay(model, null);
        }
        redirect.addFlashAttribute("flash", "Anbiyam saved.");
        return "redirect:/anbiyam";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PERM_ANBIYAM_EDIT')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", anbiyamService.formFor(id));
        model.addAttribute("animators", anbiyamService.animatorOptions(id));
        return "anbiyam/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PERM_ANBIYAM_EDIT')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") AnbiyamForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return redisplay(model, id);
        }
        try {
            anbiyamService.update(id, form);
        } catch (BusinessException e) {
            binding.rejectValue("anbiyamName", "invalid", e.getMessage());
            return redisplay(model, id);
        }
        redirect.addFlashAttribute("flash", "Anbiyam saved.");
        return "redirect:/anbiyam";
    }

    /** POST, not GET: a delete reachable by link can be fired by anything that fetches a URL. */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('PERM_ANBIYAM_DELETE')")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            anbiyamService.delete(id);
            redirect.addFlashAttribute("flash", "Anbiyam removed.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/anbiyam";
    }

    /**
     * The dropdown has to be rebuilt whenever the form comes back with errors.
     *
     * @param anbiyamId null on the add screen, where nothing can belong to the anbiyam yet
     */
    private String redisplay(Model model, Long anbiyamId) {
        model.addAttribute("animators", anbiyamService.animatorOptions(anbiyamId));
        return "anbiyam/form";
    }
}
