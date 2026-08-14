package com.church.app.controller;

import com.church.app.entity.ActorType;
import com.church.app.security.AppUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders the shared page header for both kinds of account.
 *
 * <p>The principals are built by hand rather than read from the sample data, so these
 * tests do not break when someone signs in through the running application and changes
 * their own password -- the failure mode CLAUDE.md records.
 *
 * <p>The logo directory deliberately points at a path that does not exist, so the
 * fallback is what gets exercised; a machine with a logo file present would otherwise
 * pass for the wrong reason.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.logo-dir=./build/logo-dir-that-does-not-exist")
class PageHeaderTests {

    @Autowired
    private MockMvc mockMvc;

    private static AppUserPrincipal parishUser() {
        return AppUserPrincipal.builder()
                .userId(1L)
                .churchId(1L)
                // Synthetic, and free of characters Thymeleaf would HTML-escape.
                .churchName("Test Parish")
                .actorType(ActorType.MEMBER)
                .username("parish@example.com")
                .password("irrelevant")
                .displayName("Parish Person")
                .role("AppSA")
                .usingDefaultPassword(false)
                .locked(false)
                .active(true)
                .build();
    }

    private static AppUserPrincipal platformUser() {
        return AppUserPrincipal.builder()
                .userId(1L)
                // No church: that is what makes this a platform account.
                .actorType(ActorType.SAAS_USER)
                .username("platform@example.com")
                .password("irrelevant")
                .displayName("Platform Person")
                .role("SaaSSAdmin")
                .usingDefaultPassword(false)
                .locked(false)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("parish header shows the church crest, its name and a POST sign-out")
    void parishHeader() throws Exception {
        mockMvc.perform(get("/dashboard").with(user(parishUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/church/logo")))
                .andExpect(content().string(containsString("Test Parish")))
                .andExpect(content().string(containsString("Parish Person")))
                .andExpect(content().string(containsString("action=\"/logout\"")));
    }

    @Test
    @DisplayName("platform header carries no church logo and signs out on the platform chain")
    void platformHeader() throws Exception {
        mockMvc.perform(get("/saas/dashboard").with(user(platformUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Platform")))
                .andExpect(content().string(containsString("action=\"/saas/logout\"")))
                // The church slot is empty for an account that belongs to no parish.
                .andExpect(content().string(not(containsString("/church/logo"))));
    }

    @Test
    @DisplayName("a church with no logo file falls back to the shipped default")
    void logoFallsBackToDefault() throws Exception {
        mockMvc.perform(get("/church/logo").with(user(parishUser())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                // Private: this is one tenant's image and must never sit in a shared cache.
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    @DisplayName("the logo endpoint is not reachable without signing in")
    void logoRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/church/logo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }
}
