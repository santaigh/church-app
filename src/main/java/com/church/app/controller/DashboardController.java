package com.church.app.controller;

import com.church.app.security.AppUserPrincipal;
import com.church.app.security.SelectedChurch;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing pages after sign-in.
 *
 * <p>Placeholders for now -- they confirm who is signed in and what they may do, which is
 * what 5c needs to demonstrate. The real dashboards arrive with their own module.
 */
@Controller
public class DashboardController {

    private final SelectedChurch selectedChurch;

    public DashboardController(SelectedChurch selectedChurch) {
        this.selectedChurch = selectedChurch;
    }

    /** Root goes to the parish dashboard, which the security chain gates. */
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    /**
     * The parish dashboard, reached two ways: a member signs in and lands here, and a
     * platform user arrives after choosing a church.
     *
     * <p>A platform user who has chosen nothing has no parish to show, so they are sent
     * back to pick one rather than shown an empty page.
     */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal,
                            HttpServletRequest request,
                            Model model) {
        if (principal.isPlatformUser() && selectedChurch.from(request).isEmpty()) {
            return "redirect:/saas/dashboard";
        }
        addPrincipal(model, principal);
        model.addAttribute("churchName", selectedChurch.from(request)
                .map(SelectedChurch.Selection::churchName)
                .orElse(principal.getChurchName()));
        return "dashboard";
    }

    private void addPrincipal(Model model, AppUserPrincipal principal) {
        model.addAttribute("displayName", principal.getDisplayName());
        model.addAttribute("roleCode", principal.getRoleCode());
        model.addAttribute("actorType", principal.getActorType());
        model.addAttribute("churchId", principal.getChurchId());
        model.addAttribute("usingDefaultPassword", principal.isUsingDefaultPassword());
        model.addAttribute("authorities", principal.getAuthorities().stream()
                .map(Object::toString).sorted().toList());
    }
}
