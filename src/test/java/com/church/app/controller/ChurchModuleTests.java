package com.church.app.controller;

import com.church.app.entity.ActorType;
import com.church.app.entity.Church;
import com.church.app.entity.Member;
import com.church.app.repository.ChurchRepository;
import com.church.app.repository.MemberRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creating and removing the parishes themselves.
 *
 * <p>Transactional: a parish created here is rolled back, along with the anbiyam, family
 * and administrator that come with it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChurchModuleTests {

    private static final Long CHENNAI = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChurchRepository churchRepository;

    @Autowired
    private MemberRepository memberRepository;

    private static AppUserPrincipal platform(String role, String... permissions) {
        AppUserPrincipal.Builder builder = AppUserPrincipal.builder()
                .userId(1L)
                .actorType(ActorType.SAAS_USER)
                .username(role.toLowerCase() + "@example.com")
                .password("x")
                .displayName(role + " Person")
                .role(role)
                .usingDefaultPassword(false).locked(false).active(true);
        for (String permission : permissions) {
            builder.permission(permission);
        }
        return builder.build();
    }

    private static AppUserPrincipal superAdmin() {
        return platform("SaaSSAdmin", "PERM_CHURCH_VIEW", "PERM_CHURCH_ADD",
                "PERM_CHURCH_EDIT", "PERM_CHURCH_DELETE");
    }

    private static AppUserPrincipal support() {
        return platform("SaaSUser", "PERM_CHURCH_VIEW");
    }

    private Church byName(String name) {
        return churchRepository.findAll().stream()
                .filter(church -> name.equals(church.getChurchName()))
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("a new parish comes with an account that can sign into it")
    void newParishGetsAnAdministrator() throws Exception {
        mockMvc.perform(post("/saas/churches/new").with(user(superAdmin())).with(csrf())
                        .param("churchName", "St. Anne's Church")
                        .param("city", "Vellore")
                        .param("administrator.firstName", "Fr. Joseph")
                        .param("administrator.gender", "MALE")
                        .param("administrator.mobile", "9840555001"))
                .andExpect(status().is3xxRedirection());

        Church created = byName("St. Anne's Church");

        Member admin = memberRepository.findByChurchIdAndDeletedFlagFalse(created.getId()).stream()
                .findFirst().orElseThrow();
        assertEquals("AppSA", admin.getRole().getRoleCode());
        assertEquals("+919840555001", admin.getMobile(), "normalised, as every mobile is");
        assertTrue(admin.isUsingDefaultPassword(), "forced to choose their own at first sign-in");

        // The deadlock this exists to break: a member needs a family and an anbiyam, and
        // creating either needs somebody already signed in to that parish.
        assertEquals("Parish Office", admin.getAnbiyam().getAnbiyamName());
        assertEquals("FAM-000", admin.getFamily().getFamilyCode());
    }

    @Test
    @DisplayName("an administrator with no email and no mobile is refused")
    void administratorNeedsAnIdentifier() throws Exception {
        mockMvc.perform(post("/saas/churches/new").with(user(superAdmin())).with(csrf())
                        .param("churchName", "St. Jude's Church")
                        .param("city", "Salem")
                        .param("administrator.firstName", "Fr. Nobody"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("email or a mobile")));

        // The parish and its administrator are created in one transaction, so the failure
        // discards both. That cannot be asserted from here: MockMvc runs inside this
        // test's own transaction, so the rolled-back row is still visible to this thread.
        // What is checkable is that the form comes back with the reason, above.
    }

    @Test
    @DisplayName("a parish may be created without an administrator, and says so")
    void administratorIsOptional() throws Exception {
        mockMvc.perform(post("/saas/churches/new").with(user(superAdmin())).with(csrf())
                        .param("churchName", "St. Thomas Church")
                        .param("city", "Kanchipuram"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("flash",
                                containsString("nobody can sign into it yet")));
    }

    @Test
    @DisplayName("the town is required, because two parishes may share a name")
    void townIsRequired() throws Exception {
        mockMvc.perform(post("/saas/churches/new").with(user(superAdmin())).with(csrf())
                        .param("churchName", "St. Mary's Cathedral"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("town is needed")));
    }

    // ------------------------------------------------------------- permissions

    @Test
    @DisplayName("support staff can look but not touch")
    void supportCannotAdministerParishes() throws Exception {
        mockMvc.perform(get("/saas/dashboard").with(user(support())))
                .andExpect(status().isOk())
                // No action offered...
                .andExpect(content().string(not(containsString("/saas/churches/new"))));

        // ...and the door is shut regardless.
        mockMvc.perform(get("/saas/churches/new").with(user(support())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/saas/churches/{id}/remove", CHENNAI)
                        .with(user(support())).with(csrf())
                        .param("confirmName", "St. Mary's Cathedral"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- substations

    @Test
    @DisplayName("a substation is added under its parish and holds nothing of its own")
    void substationsHangOffTheParish() throws Exception {
        mockMvc.perform(post("/saas/churches/{id}/substations", CHENNAI)
                        .with(user(superAdmin())).with(csrf())
                        .param("name", "Holy Cross Chapel")
                        .param("location", "Adyar"))
                .andExpect(status().is3xxRedirection());

        Church chapel = byName("Holy Cross Chapel");
        assertEquals(CHENNAI, chapel.getParentChurch().getId());
        assertFalse(chapel.isStation());
        assertEquals(0, memberRepository.countByChurchIdAndDeletedFlagFalse(chapel.getId()));
    }

    // ---------------------------------------------------------- remove/restore

    @Test
    @DisplayName("removing a parish needs its name typed, not a button clicked")
    void removalNeedsTheNameTyped() throws Exception {
        mockMvc.perform(post("/saas/churches/{id}/remove", CHENNAI)
                        .with(user(superAdmin())).with(csrf())
                        .param("confirmName", "st marys"))
                .andExpect(status().is3xxRedirection());

        assertFalse(churchRepository.findById(CHENNAI).orElseThrow().isDeletedFlag(),
                "a near-miss is not a confirmation");
    }

    @Test
    @DisplayName("a removed parish keeps everything it held, and gets it all back")
    void removalRetainsTheRegister() throws Exception {
        long membersBefore = memberRepository.countByChurchIdAndDeletedFlagFalse(CHENNAI);
        assertTrue(membersBefore > 0);

        mockMvc.perform(post("/saas/churches/{id}/remove", CHENNAI)
                        .with(user(superAdmin())).with(csrf())
                        .param("confirmName", "St. Mary's Cathedral"))
                .andExpect(status().is3xxRedirection());

        assertTrue(churchRepository.findById(CHENNAI).orElseThrow().isDeletedFlag());
        // Nothing cascaded: the register is intact, merely unreachable.
        assertEquals(membersBefore, memberRepository.countByChurchIdAndDeletedFlagFalse(CHENNAI));

        mockMvc.perform(post("/saas/churches/{id}/restore", CHENNAI)
                        .with(user(superAdmin())).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertFalse(churchRepository.findById(CHENNAI).orElseThrow().isDeletedFlag());
        assertEquals(membersBefore, memberRepository.countByChurchIdAndDeletedFlagFalse(CHENNAI));
    }

    @Test
    @DisplayName("a removed parish cannot be entered")
    void removedParishesAreNotEnterable() throws Exception {
        mockMvc.perform(post("/saas/churches/{id}/remove", CHENNAI)
                        .with(user(superAdmin())).with(csrf())
                        .param("confirmName", "St. Mary's Cathedral"));

        mockMvc.perform(post("/saas/enter-church").with(user(superAdmin())).with(csrf())
                        .param("churchId", String.valueOf(CHENNAI)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/saas/dashboard"));
    }
}
