package com.church.app.controller;

import com.church.app.security.AppUserPrincipal;
import com.church.app.security.CurrentUser;
import com.church.app.security.SelectedChurch;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

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
                platform ? "/saas/change-password" : "/change-password");
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
                             String changePasswordUrl) {
    }
}
