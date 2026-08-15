package com.church.app.security;

import com.church.app.entity.ActorType;
import com.church.app.entity.Church;
import com.church.app.filter.TenantContextFilter;
import com.church.app.repository.ChurchRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A platform account narrowing itself to one parish.
 *
 * <p>The scoping assertions matter most. Branding that says one parish while the queries
 * still return every parish would be worse than no selection at all -- it would invite
 * someone to edit the wrong church's records believing they were in the right one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChurchSelectionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChurchRepository churchRepository;

    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static AppUserPrincipal platformUser() {
        return AppUserPrincipal.builder()
                .userId(1L)
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

    private static AppUserPrincipal parishUser() {
        return AppUserPrincipal.builder()
                .userId(1L)
                .churchId(1L)
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

    private Long stationId(String name) {
        return churchRepository.findByDeletedFlagFalseOrderByChurchNameAsc().stream()
                .filter(c -> c.getChurchName().equals(name))
                .map(Church::getId)
                .findFirst()
                .orElseThrow();
    }

    /** Runs the tenant filter over one request and reports the scope seen inside it. */
    private Long scopeSeenBy(AppUserPrincipal principal, Long selectedChurchId) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new PreAuthenticatedAuthenticationToken(principal, "n/a",
                        AuthorityUtils.createAuthorityList("ROLE_" + principal.getRoleCode())));

        MockHttpServletRequest request = new MockHttpServletRequest();
        SelectedChurch selectedChurch = new SelectedChurch();
        if (selectedChurchId != null) {
            selectedChurch.select(request, new SelectedChurch.Selection(selectedChurchId, "Chosen"));
        }

        AtomicReference<Long> seen = new AtomicReference<>();
        AtomicReference<Boolean> platformWide = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seen.set(TenantContext.currentChurchId().orElse(null));
            platformWide.set(TenantContext.isPlatformWide());
        };

        new TenantContextFilter(selectedChurch)
                .doFilter(request, new MockHttpServletResponse(), chain);

        return platformWide.get() ? null : seen.get();
    }

    @Test
    @DisplayName("a platform user inside a parish is scoped to it, exactly as a member would be")
    void selectionScopesThePlatformUser() throws Exception {
        assertEquals(2L, scopeSeenBy(platformUser(), 2L));
    }

    @Test
    @DisplayName("a platform user who has chosen nothing stays platform-wide")
    void noSelectionMeansPlatformWide() throws Exception {
        assertEquals(null, scopeSeenBy(platformUser(), null));
    }

    @Test
    @DisplayName("a selection in a parish user's session cannot move them to another church")
    void selectionIsIgnoredForParishUsers() throws Exception {
        // Their church comes from their own record. If a stray selection could override
        // it, a member would be one session attribute away from another parish's data.
        assertEquals(1L, scopeSeenBy(parishUser(), 999L));
    }

    @Test
    @DisplayName("a platform user with no parish chosen is sent to choose one")
    void dashboardRedirectsUntilAParishIsChosen() throws Exception {
        mockMvc.perform(get("/dashboard").with(user(platformUser())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/saas/dashboard"));
    }

    @Test
    @DisplayName("entering a parish brands the header with it and serves its crest")
    void enteringAParishSwitchesTheHeader() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long stMarys = stationId("St. Mary's Cathedral");

        mockMvc.perform(post("/saas/enter-church").param("churchId", String.valueOf(stMarys))
                        .session(session).with(user(platformUser())).with(csrf()))
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(get("/dashboard").session(session).with(user(platformUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("St. Mary&#39;s Cathedral")))
                .andExpect(content().string(containsString("/church/logo")))
                // The module menu only exists inside a parish.
                .andExpect(content().string(containsString("Switch church")));
    }

    @Test
    @DisplayName("a substation cannot be entered -- there is nothing inside one to administer")
    void substationsAreNotEnterable() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long chapel = stationId("St. Anthony's Chapel");

        mockMvc.perform(post("/saas/enter-church").param("churchId", String.valueOf(chapel))
                        .session(session).with(user(platformUser())).with(csrf()))
                .andExpect(redirectedUrl("/saas/dashboard"));

        // Nothing was stored, so the account is still outside every parish.
        assertTrue(new SelectedChurch().from(newRequestWith(session)).isEmpty());
    }

    @Test
    @DisplayName("returning to the platform dashboard leaves the parish")
    void returningToThePlatformDashboardClearsTheSelection() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long stMarys = stationId("St. Mary's Cathedral");

        mockMvc.perform(post("/saas/enter-church").param("churchId", String.valueOf(stMarys))
                .session(session).with(user(platformUser())).with(csrf()));
        assertFalse(new SelectedChurch().from(newRequestWith(session)).isEmpty());

        mockMvc.perform(get("/saas/dashboard").session(session).with(user(platformUser())))
                .andExpect(status().isOk());

        assertTrue(new SelectedChurch().from(newRequestWith(session)).isEmpty(),
                "the platform dashboard sits outside every parish, so arriving there leaves the one that was open");
    }

    @Test
    @DisplayName("the parish list names the town, because two parishes may share a name")
    void parishListShowsTheTown() throws Exception {
        mockMvc.perform(get("/saas/dashboard").with(user(platformUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("St. Mary&#39;s Cathedral")))
                .andExpect(content().string(containsString("Chennai")))
                // Substations are shown as detail under their station, never as entries.
                .andExpect(content().string(containsString("substation")));
    }

    @Test
    @DisplayName("the crest follows the entered parish, not the account")
    void logoFollowsTheEnteredParish() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Long stMarys = stationId("St. Mary's Cathedral");

        mockMvc.perform(post("/saas/enter-church").param("churchId", String.valueOf(stMarys))
                .session(session).with(user(platformUser())).with(csrf()));

        mockMvc.perform(get("/church/logo").session(session).with(user(platformUser())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    private static MockHttpServletRequest newRequestWith(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }
}
