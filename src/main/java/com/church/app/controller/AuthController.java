package com.church.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the two login pages and the access-denied page.
 *
 * <p>The forms themselves are processed by Spring Security's filters, not by handler
 * methods here -- these methods only render.
 *
 * <p>Every failure produces the same message regardless of cause, so the page cannot be
 * used to discover which email addresses and mobile numbers belong to real accounts.
 */
@Controller
public class AuthController {

    private static final String GENERIC_FAILURE_MESSAGE =
            "Invalid credentials. Please check and try again.";

    /**
     * Shown only for a locked account. Unlike the generic message this does confirm the
     * account's state -- a deliberate choice, so a parishioner is not left retrying
     * forever with no explanation. There is no self-service unlock: forgot-password
     * resets the password but does not clear the lock.
     */
    private static final String LOCKED_MESSAGE =
            "This account has been locked after too many failed sign-in attempts. "
            + "Please contact your parish office to have it unlocked.";

    @GetMapping("/login")
    public String memberLogin(@RequestParam(required = false) String error,
                              @RequestParam(required = false) String logout,
                              @RequestParam(required = false) String expired,
                              @RequestParam(required = false) String locked,
                              Model model) {
        addFeedback(model, error, logout, expired, locked);
        return "auth/login";
    }

    @GetMapping("/saas/login")
    public String saasLogin(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            @RequestParam(required = false) String expired,
                            @RequestParam(required = false) String locked,
                            Model model) {
        addFeedback(model, error, logout, expired, locked);
        return "auth/saas-login";
    }

    @GetMapping("/access-denied")
    public String accessDenied(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("message",
                "You do not have permission to view this page.");
        model.addAttribute("code", "ACCESS_DENIED");
        return "error/403";
    }

    private void addFeedback(Model model, String error, String logout, String expired, String locked) {
        if (locked != null) {
            model.addAttribute("errorMessage", LOCKED_MESSAGE);
        } else if (error != null) {
            model.addAttribute("errorMessage", GENERIC_FAILURE_MESSAGE);
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "You have been signed out.");
        }
        if (expired != null) {
            // Shown when the same account signed in elsewhere, ending this session.
            model.addAttribute("infoMessage",
                    "Your session ended because this account signed in on another device.");
        }
    }
}
