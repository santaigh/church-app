package com.church.app.controller;

import com.church.app.dto.ChangePasswordForm;
import com.church.app.exception.BusinessException;
import com.church.app.security.AppUserPrincipal;
import com.church.app.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Change-password and forgot-password, for both account types.
 *
 * <p>The two security chains are independent, so each needs its own URLs -- a
 * {@code /saas/**} path is handled by the platform chain and everything else by the
 * parish chain. The page templates are shared and parameterised.
 */
@Controller
public class PasswordController {

    private static final Logger log = LoggerFactory.getLogger(PasswordController.class);

    /** Shown whether or not the account exists, so the form reveals nothing. */
    private static final String RESET_ACKNOWLEDGEMENT =
            "If that email address or mobile number matches an account, its password has been reset. "
            + "Please sign in with the password provided by your parish office.";

    private final PasswordService passwordService;
    private final UserDetailsService memberUserDetailsService;
    private final UserDetailsService saasUserDetailsService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public PasswordController(PasswordService passwordService,
                              UserDetailsService memberUserDetailsService,
                              UserDetailsService saasUserDetailsService) {
        this.passwordService = passwordService;
        this.memberUserDetailsService = memberUserDetailsService;
        this.saasUserDetailsService = saasUserDetailsService;
    }

    // ---------------------------------------------------------- change password

    @GetMapping("/change-password")
    public String changePassword(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        prepareChangeForm(model, principal, false);
        return "auth/change-password";
    }

    @GetMapping("/saas/change-password")
    public String saasChangePassword(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        prepareChangeForm(model, principal, true);
        return "auth/change-password";
    }

    @PostMapping("/change-password")
    public String submitChangePassword(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @ModelAttribute ChangePasswordForm form,
                                       Model model, HttpServletRequest request, HttpServletResponse response,
                                       RedirectAttributes redirectAttributes) {
        return handleChange(principal, form, model, request, response, redirectAttributes, false);
    }

    @PostMapping("/saas/change-password")
    public String submitSaasChangePassword(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @ModelAttribute ChangePasswordForm form,
                                           Model model, HttpServletRequest request, HttpServletResponse response,
                                           RedirectAttributes redirectAttributes) {
        return handleChange(principal, form, model, request, response, redirectAttributes, true);
    }

    private String handleChange(AppUserPrincipal principal, ChangePasswordForm form, Model model,
                                HttpServletRequest request, HttpServletResponse response,
                                RedirectAttributes redirectAttributes, boolean platform) {
        try {
            passwordService.changeOwnPassword(principal, form.getCurrentPassword(),
                    form.getNewPassword(), form.getConfirmPassword());
        } catch (BusinessException ex) {
            // Redisplay the form with the reason rather than bouncing to an error page.
            prepareChangeForm(model, principal, platform);
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/change-password";
        }

        refreshAuthentication(principal, request, response, platform);
        redirectAttributes.addFlashAttribute("infoMessage", "Your password has been changed.");
        return platform ? "redirect:/saas/dashboard" : "redirect:/dashboard";
    }

    /**
     * Rebuilds the session's principal from the database.
     *
     * <p>Without this the signed-in principal still carries {@code passwordFlag = false},
     * so the interceptor would keep redirecting the user back to this page after they had
     * successfully changed their password.
     */
    private void refreshAuthentication(AppUserPrincipal principal, HttpServletRequest request,
                                       HttpServletResponse response, boolean platform) {
        UserDetailsService source = platform ? saasUserDetailsService : memberUserDetailsService;
        UserDetails refreshed = source.loadUserByUsername(principal.getUsername());

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                refreshed, refreshed.getPassword(), refreshed.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        log.debug("Security context refreshed after password change for '{}'", principal.getUsername());
    }

    private void prepareChangeForm(Model model, AppUserPrincipal principal, boolean platform) {
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        model.addAttribute("platform", platform);
        model.addAttribute("formAction", platform ? "/saas/change-password" : "/change-password");
        model.addAttribute("cancelAction", platform ? "/saas/dashboard" : "/dashboard");
        model.addAttribute("logoutAction", platform ? "/saas/logout" : "/logout");
        model.addAttribute("forced", principal != null && principal.isUsingDefaultPassword());
        model.addAttribute("displayName", principal != null ? principal.getDisplayName() : null);
    }

    // ---------------------------------------------------------- forgot password

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        prepareForgotForm(model, false);
        return "auth/forgot-password";
    }

    @GetMapping("/saas/forgot-password")
    public String saasForgotPassword(Model model) {
        prepareForgotForm(model, true);
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String submitForgotPassword(@RequestParam(required = false) String identifier,
                                       RedirectAttributes redirectAttributes) {
        passwordService.resetToDefaultPassword(identifier, false);
        redirectAttributes.addFlashAttribute("infoMessage", RESET_ACKNOWLEDGEMENT);
        return "redirect:/login";
    }

    @PostMapping("/saas/forgot-password")
    public String submitSaasForgotPassword(@RequestParam(required = false) String identifier,
                                           RedirectAttributes redirectAttributes) {
        passwordService.resetToDefaultPassword(identifier, true);
        redirectAttributes.addFlashAttribute("infoMessage", RESET_ACKNOWLEDGEMENT);
        return "redirect:/saas/login";
    }

    private void prepareForgotForm(Model model, boolean platform) {
        model.addAttribute("platform", platform);
        model.addAttribute("formAction", platform ? "/saas/forgot-password" : "/forgot-password");
        model.addAttribute("backAction", platform ? "/saas/login" : "/login");
    }
}
