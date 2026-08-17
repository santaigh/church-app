package com.church.app.controller;

import com.church.app.entity.Operation;
import com.church.app.entity.Resource;
import com.church.app.security.AppUserPrincipal;
import com.church.app.security.CurrentUser;
import com.church.app.security.SelectedChurch;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Puts everything the page header needs into the model of every view.
 *
 * <p>Without this, each controller would have to remember to add the display name, role
 * and church before returning a view -- and the header would silently lose them the first
 * time someone forgot. The fragment asks for {@code ${header}} and gets it anywhere.
 *
 * <p>Null for anonymous requests, which is what the sign-in and error pages are; the
 * fragment is simply not rendered there.
 */
@ControllerAdvice
public class HeaderModelAdvice {

    private final SelectedChurch selectedChurch;

    public HeaderModelAdvice(SelectedChurch selectedChurch) {
        this.selectedChurch = selectedChurch;
    }

    @ModelAttribute("header")
    public HeaderInfo header() {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        if (principal == null) {
            return null;
        }

        boolean platform = principal.isPlatformUser();
        Optional<SelectedChurch.Selection> selection = platform
                ? selectedChurch.current()
                : Optional.empty();

        // A parish user is always inside their own church. A platform user is inside one
        // only after choosing it -- until then there is no crest, no name and no menu,
        // because there is no parish to show them for.
        String churchName = platform
                ? selection.map(SelectedChurch.Selection::churchName).orElse(null)
                : principal.getChurchName();
        boolean insideChurch = churchName != null;

        return new HeaderInfo(
                principal.getDisplayName(),
                principal.getRoleCode(),
                churchName,
                platform,
                insideChurch,
                // The two chains have separate URLs for these; the template should not
                // have to know which one it is rendering under.
                platform ? "/saas/logout" : "/logout",
                platform ? "/saas/change-password" : "/change-password",
                insideChurch ? menuFor(principal) : List.of(),
                principal.getAuthorities().stream()
                        .map(granted -> granted.getAuthority())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    /**
     * The modules this account may open.
     *
     * <p>Built from the permissions loaded at sign-in, so a read-only parish account
     * simply does not see what it cannot use. This is presentation only -- every
     * controller carries its own {@code @PreAuthorize}, because a hidden link is not a
     * closed door.
     */
    private List<MenuItem> menuFor(AppUserPrincipal principal) {
        String path = currentPath();
        List<MenuItem> items = new ArrayList<>();

        add(items, principal, Resource.DASHBOARD, "menu.dashboard", "/dashboard", path);
        add(items, principal, Resource.ANBIYAM, "menu.anbiyam", "/anbiyam", path);
        add(items, principal, Resource.PARISH_PRIEST, "menu.parishPriest", "/parish-priest", path);
        add(items, principal, Resource.MEMBER, "menu.members", "/members", path);
        add(items, principal, Resource.FAMILY, "menu.families", "/families", path);
        add(items, principal, Resource.PAYMENT, "menu.payments", "/payments", path);

        return List.copyOf(items);
    }

    private void add(List<MenuItem> items,
                     AppUserPrincipal principal,
                     Resource resource,
                     String labelKey,
                     String url,
                     String currentPath) {
        String authority = "PERM_" + resource.name() + "_" + Operation.VIEW.name();
        boolean permitted = principal.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority));
        if (!permitted) {
            return;
        }
        boolean active = url != null && currentPath != null && currentPath.startsWith(url);
        items.add(new MenuItem(labelKey, url, active));
    }

    private static String currentPath() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRequestURI()
                : null;
    }

    /** @param url null for a module that is permitted but not yet built */
    public record MenuItem(String labelKey, String url, boolean active) {

        public boolean isBuilt() {
            return url != null;
        }
    }

    /**
     * @param churchName   null for a platform user who has not entered a parish
     * @param platform     true for a {@code saas_user} account
     * @param insideChurch whether there is a parish context: drives the crest and the menu
     */
    public record HeaderInfo(String displayName,
                             String roleCode,
                             String churchName,
                             boolean platform,
                             boolean insideChurch,
                             String logoutUrl,
                             String changePasswordUrl,
                             List<MenuItem> menu,
                             java.util.Set<String> permissions) {

        /**
         * Lets a template hide a control the account cannot use, e.g.
         * {@code th:if="${header.can('PERM_ANBIYAM_ADD')}"}. Presentation only -- the
         * controller's {@code @PreAuthorize} is what actually refuses the request.
         */
        public boolean can(String authority) {
            return permissions.contains(authority);
        }
    }
}
