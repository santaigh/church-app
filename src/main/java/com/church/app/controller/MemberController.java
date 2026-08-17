package com.church.app.controller;

import com.church.app.dto.MemberExtForm;
import com.church.app.dto.MemberForm;
import com.church.app.dto.PageView;
import com.church.app.entity.FamilyRole;
import com.church.app.exception.BusinessException;
import com.church.app.service.MemberService;
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

/** Parishioners of the church in scope. */
@Controller
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /**
     * @param family  narrows the list to one family -- what clicking a family opens
     * @param anbiyam narrows it to one anbiyam, which is how an anbiyam shows its members
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_MEMBER_VIEW')")
    public String list(@RequestParam(name = "family", required = false) Long family,
                       @RequestParam(name = "anbiyam", required = false) Long anbiyam,
                       @RequestParam(name = "name", required = false) String name,
                       @RequestParam(name = "familyText", required = false) String familyText,
                       @RequestParam(name = "role", required = false) FamilyRole role,
                       @RequestParam(name = "anbiyamText", required = false) String anbiyamText,
                       @RequestParam(name = "mobile", required = false) String mobile,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       @RequestParam(name = "size", defaultValue = "50") int size,
                       Model model) {
        MemberService.MemberSearch search =
                new MemberService.MemberSearch(name, familyText, role, anbiyamText, mobile);

        model.addAttribute("paged", memberService.list(family, anbiyam, search, page, pageSize(size)));
        model.addAttribute("search", search);
        // Carried on every paging link, so moving to page 2 keeps the search rather than
        // quietly showing a different set of people.
        model.addAttribute("params", QueryParams.of()
                .add("family", family).add("anbiyam", anbiyam)
                .add("name", name).add("familyText", familyText)
                .add("role", role).add("anbiyamText", anbiyamText)
                .add("mobile", mobile)
                .toString());
        model.addAttribute("familyRoles", FamilyRole.values());
        model.addAttribute("filterLabel", memberService.filterLabel(family, anbiyam));
        // These pages open in a new tab, where the browser's Back is dead -- there is no
        // history behind them. The way back has to be on the page.
        if (anbiyam != null) {
            model.addAttribute("backUrl", "/anbiyam");
            model.addAttribute("backKey", "member.backToAnbiyam");
        } else if (family != null) {
            model.addAttribute("backUrl", "/members");
            model.addAttribute("backKey", "member.backToAll");
        }
        return "member/list";
    }

    /** Everything about one member: identity, their record, and the rest of it. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_MEMBER_VIEW')")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("member", memberService.detail(id));
        model.addAttribute("ext", memberService.extraDetail(id).orElse(null));
        return "member/view";
    }

    /**
     * The additional details used to be a page of their own. Kept as a redirect so links
     * already saved or bookmarked still land on the section rather than a 404.
     */
    @GetMapping("/{id}/details")
    @PreAuthorize("hasAuthority('PERM_MEMBER_VIEW')")
    public String extraDetail(@PathVariable Long id) {
        return "redirect:/members/" + id + "#additional";
    }

    @GetMapping("/{id}/details/edit")
    @PreAuthorize("hasAuthority('PERM_MEMBER_EDIT')")
    public String editExtraDetail(@PathVariable Long id, Model model) {
        model.addAttribute("member", memberService.detail(id));
        model.addAttribute("form", memberService.extraDetailForm(id));
        return "member/ext-form";
    }

    @PostMapping("/{id}/details/edit")
    @PreAuthorize("hasAuthority('PERM_MEMBER_EDIT')")
    public String updateExtraDetail(@PathVariable Long id,
                                    @Valid @ModelAttribute("form") MemberExtForm form,
                                    BindingResult binding,
                                    Model model,
                                    RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("member", memberService.detail(id));
            return "member/ext-form";
        }
        memberService.saveExtraDetail(id, form);
        redirect.addFlashAttribute("flash", "Additional details saved.");
        return "redirect:/members/" + id;
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PERM_MEMBER_ADD')")
    public String addForm(Model model) {
        model.addAttribute("form", new MemberForm());
        return withOptions(model);
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('PERM_MEMBER_ADD')")
    public String create(@Valid @ModelAttribute("form") MemberForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return withOptions(model);
        }
        try {
            memberService.create(form);
        } catch (BusinessException e) {
            binding.rejectValue("firstName", "invalid", e.getMessage());
            return withOptions(model);
        }
        redirect.addFlashAttribute("flash", "Member saved.");
        return "redirect:/members";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PERM_MEMBER_EDIT')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", memberService.formFor(id));
        return withOptions(model);
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('PERM_MEMBER_EDIT')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") MemberForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return withOptions(model);
        }
        try {
            memberService.update(id, form);
        } catch (BusinessException e) {
            binding.rejectValue("firstName", "invalid", e.getMessage());
            return withOptions(model);
        }
        redirect.addFlashAttribute("flash", "Member saved.");
        return "redirect:/members/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('PERM_MEMBER_DELETE')")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            memberService.delete(id);
            redirect.addFlashAttribute("flash", "Member removed.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/members";
    }

    /** Ignores a size nobody offered, so a hand-typed {@code &size=100000} cannot ask for
     *  every row in the parish at once. */
    private static int pageSize(int requested) {
        return PageView.SIZES.contains(requested) ? requested : PageView.DEFAULT_SIZE;
    }

    private String withOptions(Model model) {
        model.addAttribute("families", memberService.families());
        model.addAttribute("anbiyams", memberService.anbiyams());
        model.addAttribute("familyRoles", FamilyRole.values());
        model.addAttribute("roles", memberService.assignableRoles());
        return "member/form";
    }
}
