package com.church.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Smoke-test controller for the initial scaffold.
 *
 * <p>Kept public so the app can be confirmed alive without signing in. {@code /} now
 * belongs to {@link DashboardController} and redirects into the authenticated area.
 */
@Controller
public class HelloController {

    private static final Logger log = LoggerFactory.getLogger(HelloController.class);

    @GetMapping("/hello")
    public String hello(@RequestParam(name = "name", defaultValue = "World") String name, Model model) {
        log.debug("Rendering hello view for name={}", name);
        model.addAttribute("name", name);
        model.addAttribute("appName", "Church App");
        return "hello";
    }
}
