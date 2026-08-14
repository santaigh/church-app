package com.church.app.controller;

import com.church.app.security.AppUserPrincipal;
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

    /** Root goes to the parish dashboard, which the security chain gates. */
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        addPrincipal(model, principal);
        return "dashboard";
    }

    @GetMapping("/saas/dashboard")
    public String saasDashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        addPrincipal(model, principal);
        return "saas-dashboard";
    }

    private void addPrincipal(Model model, AppUserPrincipal principal) {
        model.addAttribute("displayName", principal.getDisplayName());
        model.addAttribute("roleCode", principal.getRoleCode());
        model.addAttribute("actorType", principal.getActorType());
        model.addAttribute("churchId", principal.getChurchId());
        model.addAttribute("churchName", principal.getChurchName());
        model.addAttribute("usingDefaultPassword", principal.isUsingDefaultPassword());
        model.addAttribute("authorities", principal.getAuthorities().stream()
                .map(Object::toString).sorted().toList());
    }
}
