package com.church.app.controller;

import com.church.app.dto.PageView;
import com.church.app.service.FamilyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The households of the parish.
 *
 * <p>View only. Clicking a family opens its members, which is the question a family list
 * is usually asked.
 */
@Controller
@RequestMapping("/families")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_FAMILY_VIEW')")
    public String list(@RequestParam(name = "name", required = false) String name,
                       @RequestParam(name = "code", required = false) String code,
                       @RequestParam(name = "anbiyamText", required = false) String anbiyamText,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       @RequestParam(name = "size", defaultValue = "50") int size,
                       Model model) {
        FamilyService.FamilySearch search = new FamilyService.FamilySearch(name, code, anbiyamText);

        model.addAttribute("paged", familyService.list(search, page,
                PageView.SIZES.contains(size) ? size : PageView.DEFAULT_SIZE));
        model.addAttribute("search", search);
        model.addAttribute("params", QueryParams.of()
                .add("name", name).add("code", code).add("anbiyamText", anbiyamText)
                .toString());
        return "family/list";
    }
}
