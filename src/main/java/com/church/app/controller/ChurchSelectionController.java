package com.church.app.controller;

import com.church.app.entity.AuditEventType;
import com.church.app.security.AppUserPrincipal;
import com.church.app.security.SelectedChurch;
import com.church.app.security.TenantContext;
import com.church.app.service.AuditService;
import com.church.app.service.ChurchDirectoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * The platform dashboard: choose a parish, then work inside it.
 *
 * <p>A {@code saas_user} has no church of its own, so until one is chosen there is
 * nothing parish-shaped to show. Choosing narrows the account to that church for the rest
 * of the session -- branding and queries alike, so the header can never claim one parish
 * while the data belongs to another.
 */
@Controller
public class ChurchSelectionController {

    private static final Logger log = LoggerFactory.getLogger(ChurchSelectionController.class);

    private final ChurchDirectoryService churchDirectoryService;
    private final SelectedChurch selectedChurch;
    private final AuditService auditService;

    public ChurchSelectionController(ChurchDirectoryService churchDirectoryService,
                                     SelectedChurch selectedChurch,
                                     AuditService auditService) {
        this.churchDirectoryService = churchDirectoryService;
        this.selectedChurch = selectedChurch;
        this.auditService = auditService;
    }

    /**
     * Reaching the platform dashboard means leaving whichever parish was open -- it is
     * the one page that sits outside them all, and its counts have to span every church.
     *
     * <p>The scope is reset for this request too, not only in the session: the tenant
     * filter ran before this method, so without the reset the parish just left would
     * still be filtering the counts shown on this very page.
     */
    @GetMapping("/saas/dashboard")
    public String dashboard(@RequestParam(name = "church", required = false) Long churchId,
                            @AuthenticationPrincipal AppUserPrincipal principal,
                            HttpServletRequest request,
                            Model model) {
        selectedChurch.from(request).ifPresent(previous -> {
            selectedChurch.clear(request);
            auditService.event(AuditEventType.CHURCH_EXITED)
                    .actorSaasUser(principal.getUserId(), principal.getDisplayName())
                    .inChurch(previous.churchId())
                    .describe("Returned to the platform dashboard from " + previous.churchName())
                    .save();
        });
        TenantContext.setPlatformWide();

        model.addAttribute("stations", churchDirectoryService.stations());
        model.addAttribute("selectedId", churchId);
        model.addAttribute("detail", churchId == null
                ? null
                : churchDirectoryService.detail(churchId).orElse(null));
        return "saas-dashboard";
    }

    /**
     * Enters a parish. POST rather than a link: it changes what the account can see for
     * the rest of the session, which is not something a prefetched GET should do.
     */
    @PostMapping("/saas/enter-church")
    public String enterChurch(@RequestParam("churchId") Long churchId,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              HttpServletRequest request) {
        Optional<ChurchDirectoryService.ChurchDetail> target = churchDirectoryService.detail(churchId);
        if (target.isEmpty()) {
            // Deleted, absent, or a substation -- none of which can be entered. Refuse
            // rather than store an id that would silently scope every later query to
            // nothing.
            log.warn("Platform user '{}' tried to enter church {}, which is not an enterable parish",
                    principal.getUsername(), churchId);
            return "redirect:/saas/dashboard";
        }

        ChurchDirectoryService.ChurchDetail church = target.get();
        selectedChurch.select(request, new SelectedChurch.Selection(church.id(), church.name()));

        auditService.event(AuditEventType.CHURCH_ENTERED)
                .actorSaasUser(principal.getUserId(), principal.getDisplayName())
                .inChurch(church.id())
                .on("Church", church.id(), church.name())
                .describe("Now working inside " + church.name())
                .save();

        return "redirect:/dashboard";
    }
}
