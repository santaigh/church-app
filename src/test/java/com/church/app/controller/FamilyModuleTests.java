package com.church.app.controller;

import com.church.app.entity.ActorType;
import com.church.app.security.AppUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The family list, and the dashboard tiles that lead to it. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FamilyModuleTests {

    private static final Long CHENNAI = 1L;

    @Autowired
    private MockMvc mockMvc;

    private static AppUserPrincipal parishUser(String... permissions) {
        AppUserPrincipal.Builder builder = AppUserPrincipal.builder()
                .userId(99L)
                .churchId(CHENNAI)
                .churchName("St. Mary's Cathedral")
                .actorType(ActorType.MEMBER)
                .username("parish@example.com")
                .password("irrelevant")
                .displayName("Parish Person")
                .role("AppSA")
                .usingDefaultPassword(false)
                .locked(false)
                .active(true);
        for (String permission : permissions) {
            builder.permission(permission);
        }
        return builder.build();
    }

    @Test
    @DisplayName("the family list names each household and how many are in it")
    void listShowsFamiliesWithCounts() throws Exception {
        mockMvc.perform(get("/families").with(user(parishUser("PERM_FAMILY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Arulraj Family")))
                .andExpect(content().string(containsString("FAM-001")))
                // The head of that family, and the anbiyam it belongs to.
                .andExpect(content().string(containsString("Head of family")))
                .andExpect(content().string(containsString("#1")));
    }

    @Test
    @DisplayName("a family name links to that family's members")
    void familyNameOpensItsMembers() throws Exception {
        mockMvc.perform(get("/families").with(user(parishUser("PERM_FAMILY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/members?family=1")));
    }

    @Test
    @DisplayName("the list is refused without permission, not merely hidden")
    void listNeedsPermission() throws Exception {
        mockMvc.perform(get("/families").with(user(parishUser("PERM_MEMBER_VIEW"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("dashboard tiles lead to the pages behind them")
    void dashboardTilesAreLinks() throws Exception {
        AppUserPrincipal all = parishUser("PERM_DASHBOARD_VIEW", "PERM_MEMBER_VIEW",
                "PERM_FAMILY_VIEW", "PERM_ANBIYAM_VIEW");

        mockMvc.perform(get("/dashboard").with(user(all)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stat-link")))
                .andExpect(content().string(containsString("href=\"/families\"")))
                .andExpect(content().string(containsString("href=\"/anbiyam\"")))
                .andExpect(content().string(containsString("href=\"/members\"")));
    }

    @Test
    @DisplayName("a tile whose page the account cannot open is not a link")
    void tilesFollowPermissions() throws Exception {
        // No family permission: the count still shows, but it leads nowhere.
        AppUserPrincipal noFamilies = parishUser("PERM_DASHBOARD_VIEW", "PERM_MEMBER_VIEW");

        mockMvc.perform(get("/dashboard").with(user(noFamilies)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Families")))
                .andExpect(content().string(not(containsString("href=\"/families\""))));
    }
}
