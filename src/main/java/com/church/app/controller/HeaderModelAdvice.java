package com.church.app.controller;

import com.church.app.security.AppUserPrincipal;
import com.church.app.security.CurrentUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

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

    @ModelAttribute("header")
    public HeaderInfo header() {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        if (principal == null) {
            return null;
        }

        boolean platform = principal.isPlatformUser();
        return new HeaderInfo(
                principal.getDisplayName(),
                principal.getRoleCode(),
                principal.getChurchName(),
                platform,
                // The two chains have separate URLs for these; the template should not
                // have to know which one it is rendering under.
                platform ? "/saas/logout" : "/logout",
                platform ? "/saas/change-password" : "/change-password");
    }

    /**
     * @param churchName null for platform users, who are not scoped to one parish
     * @param platform   true for a {@code saas_user} account, which shows no church logo
     */
    public record HeaderInfo(String displayName,
                             String roleCode,
                             String churchName,
                             boolean platform,
                             String logoutUrl,
                             String changePasswordUrl) {
    }
}
