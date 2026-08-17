package com.church.app.controller;

import com.church.app.security.AppUserPrincipal;
import com.church.app.security.SelectedChurch;
import com.church.app.security.TenantContext;
import com.church.app.service.ChurchDirectoryService;
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
    private final ChurchDirectoryService churchDirectoryService;

    public DashboardController(SelectedChurch selectedChurch,
                               ChurchDirectoryService churchDirectoryService) {
        this.selectedChurch = selectedChurch;
        this.churchDirectoryService = churchDirectoryService;
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

        Long churchId = TenantContext.currentChurchId().orElse(null);
        model.addAttribute("usingDefaultPassword", principal.isUsingDefaultPassword());
        model.addAttribute("parish", churchId == null
                ? null
                : churchDirectoryService.detail(churchId).orElse(null));
        return "dashboard";
    }
}
