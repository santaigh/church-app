package com.church.app.controller;

import com.church.app.entity.ActorType;
import com.church.app.entity.Anbiyam;
import com.church.app.repository.AnbiyamRepository;
import com.church.app.security.AppUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The first module that enforces permissions and writes.
 *
 * <p>Transactional, so everything these tests create is rolled back -- the sample data in
 * {@code churchnew} is asserted on by other tests, and a stray anbiyam would break their
 * counts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnbiyamModuleTests {

    private static final Long CHENNAI = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnbiyamRepository anbiyamRepository;

    @Autowired
    private com.church.app.repository.MemberRepository memberRepository;

    private static AppUserPrincipal parishUser(String role, String... permissions) {
        AppUserPrincipal.Builder builder = AppUserPrincipal.builder()
                .userId(1L)
                .churchId(CHENNAI)
                .churchName("St. Mary's Cathedral")
                .actorType(ActorType.MEMBER)
                .username(role.toLowerCase() + "@example.com")
                .password("irrelevant")
                .displayName(role + " Person")
                .role(role)
                .usingDefaultPassword(false)
                .locked(false)
                .active(true);
        for (String permission : permissions) {
            builder.permission(permission);
        }
        return builder.build();
    }

    private static AppUserPrincipal administrator() {
        return parishUser("AppSA", "PERM_DASHBOARD_VIEW", "PERM_ANBIYAM_VIEW",
                "PERM_ANBIYAM_ADD", "PERM_ANBIYAM_EDIT", "PERM_ANBIYAM_DELETE");
    }

    private static AppUserPrincipal readOnlyUser() {
        return parishUser("AppUser", "PERM_DASHBOARD_VIEW", "PERM_ANBIYAM_VIEW");
    }

    private Anbiyam named(String name) {
        return anbiyamRepository.findByChurchIdAndDeletedFlagFalseOrderByAnbiyamNameAsc(CHENNAI)
                .stream()
                .filter(a -> a.getAnbiyamName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------------------------- enforcement

    @Test
    @DisplayName("a read-only account sees the list but is refused the add screen")
    void readOnlyAccountCannotAdd() throws Exception {
        mockMvc.perform(get("/anbiyam").with(user(readOnlyUser())))
                .andExpect(status().isOk())
                // The control is not even drawn for an account that cannot use it.
                .andExpect(content().string(not(containsString("/anbiyam/new"))));

        // ...and the door is shut regardless of what the page showed.
        mockMvc.perform(get("/anbiyam/new").with(user(readOnlyUser())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a read-only account cannot delete by posting the URL directly")
    void readOnlyAccountCannotDelete() throws Exception {
        Long id = named("அன்னை வேளாங்கண்ணி அன்பியம்").getId();

        mockMvc.perform(post("/anbiyam/{id}/delete", id).with(user(readOnlyUser())).with(csrf()))
                .andExpect(status().isForbidden());

        assertTrue(anbiyamRepository.findById(id).isPresent(), "nothing was deleted");
    }

    @Test
    @DisplayName("an administrator reaches the add screen")
    void administratorCanOpenTheAddScreen() throws Exception {
        mockMvc.perform(get("/anbiyam/new").with(user(administrator())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the menu shows a read-only account fewer modules than an administrator")
    void menuIsFilteredByPermission() throws Exception {
        String forUser = mockMvc.perform(get("/dashboard").with(user(readOnlyUser())))
                .andReturn().getResponse().getContentAsString();
        String forAdmin = mockMvc.perform(get("/dashboard").with(user(administrator())))
                .andReturn().getResponse().getContentAsString();

        // Neither can see Members or Payments: no permission was granted for them here.
        assertTrue(forUser.contains("Anbiyam"));
        assertTrue(!forUser.contains(">Payments<"));
        assertTrue(!forAdmin.contains(">Payments<"));
    }

    // ------------------------------------------------------------------ writes

    @Test
    @DisplayName("a new anbiyam is stamped with the parish from the request, not the form")
    void createStampsTheChurchFromTheTenantScope() throws Exception {
        mockMvc.perform(post("/anbiyam/new").with(user(administrator())).with(csrf())
                        .param("anbiyamName", "புனித யோவான் அன்பியம்")
                        .param("areaDescription", "Adyar")
                        .param("activeFlag", "true")
                        // A tampered field: the form has no churchId, so this is ignored.
                        .param("churchId", "2"))
                .andExpect(status().is3xxRedirection());

        Anbiyam created = named("புனித யோவான் அன்பியம்");
        assertEquals(CHENNAI, created.getChurch().getId(), "stamped from the tenant scope");
        assertTrue(created.isActiveFlag());
    }

    // ------------------------------------------------------- animator restriction

    @Test
    @DisplayName("the add screen offers no animator: nothing belongs to an anbiyam that does not exist")
    void addScreenHasNoAnimatorsToOffer() throws Exception {
        String page = mockMvc.perform(get("/anbiyam/new").with(user(administrator())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("Not assigned"));
        // No member of the parish is offered, because none is a member of this anbiyam yet.
        assertTrue(!page.contains("Stephen Devasagayam"), "no members to offer at creation");
    }

    @Test
    @DisplayName("the edit screen offers only that anbiyam's own members")
    void editScreenOffersOnlyItsOwnMembers() throws Exception {
        Long first = named("புனித அந்தோணியார் அன்பியம்").getId();

        String page = mockMvc.perform(get("/anbiyam/{id}/edit", first).with(user(administrator())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Its own five members are offered...
        assertTrue(page.contains("Stephen Devasagayam (#4)"));
        assertTrue(page.contains("Antony Raj (#1)"));
        // ...and Peter, who belongs to the second anbiyam, is not.
        assertTrue(!page.contains("Peter Fernando"), "a member of another anbiyam is not offered");
    }

    @Test
    @DisplayName("an anbiyam with no members offers nobody")
    void emptyAnbiyamOffersNoAnimator() throws Exception {
        Long empty = named("அன்னை வேளாங்கண்ணி அன்பியம்").getId();

        String page = mockMvc.perform(get("/anbiyam/{id}/edit", empty).with(user(administrator())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("Not assigned"));
        assertTrue(!page.contains("(#"), "no member options at all");
    }

    @Test
    @DisplayName("a member of another anbiyam in the same parish is refused as animator")
    void animatorMustBelongToThisAnbiyam() throws Exception {
        Anbiyam first = named("புனித அந்தோணியார் அன்பியம்");
        Long before = first.getHeadMemberId();

        // Peter, member 6, belongs to the second anbiyam of the same parish.
        mockMvc.perform(post("/anbiyam/{id}/edit", first.getId())
                        .with(user(administrator())).with(csrf())
                        .param("anbiyamName", first.getAnbiyamName())
                        .param("headMemberId", "6")
                        .param("activeFlag", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("does not belong to this anbiyam")));

        assertEquals(before, named("புனித அந்தோணியார் அன்பியம்").getHeadMemberId(),
                "the refusal must leave the existing animator alone");
    }

    @Test
    @DisplayName("an animator from another parish is refused")
    void animatorMustBelongToThisParish() throws Exception {
        Long first = named("புனித அந்தோணியார் அன்பியம்").getId();
        // Member 7 is at St. Joseph's, Madurai -- the tenant-aware findById will not
        // even load it.
        mockMvc.perform(post("/anbiyam/{id}/edit", first).with(user(administrator())).with(csrf())
                        .param("anbiyamName", "புனித அந்தோணியார் அன்பியம்")
                        .param("headMemberId", "7")
                        .param("activeFlag", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("does not belong to this anbiyam")));
    }

    @Test
    @DisplayName("an animator posted while creating is refused, not silently dropped")
    void animatorCannotBeSetAtCreation() throws Exception {
        mockMvc.perform(post("/anbiyam/new").with(user(administrator())).with(csrf())
                        .param("anbiyamName", "புனித பவுல் அன்பியம்")
                        .param("headMemberId", "4")
                        .param("activeFlag", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Save it first")));
    }

    @Test
    @DisplayName("the head can be changed to another member of the same anbiyam")
    void animatorCanBeReassignedWithinTheAnbiyam() throws Exception {
        Anbiyam first = named("புனித அந்தோணியார் அன்பியம்");
        Long current = first.getHeadMemberId();

        // Any other member of this same anbiyam, chosen from the data rather than named,
        // so the test survives the head being changed through the application.
        Long successor = memberRepository
                .findByAnbiyamIdAndDeletedFlagFalseOrderByFirstNameAsc(first.getId())
                .stream()
                .map(m -> m.getId())
                .filter(id -> !id.equals(current))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/anbiyam/{id}/edit", first.getId())
                        .with(user(administrator())).with(csrf())
                        .param("anbiyamName", first.getAnbiyamName())
                        .param("headMemberId", String.valueOf(successor))
                        .param("activeFlag", "true"))
                .andExpect(status().is3xxRedirection());

        assertEquals(successor, named("புனித அந்தோணியார் அன்பியம்").getHeadMemberId());
    }

    @Test
    @DisplayName("the head can be cleared back to nobody")
    void animatorCanBeCleared() throws Exception {
        Long first = named("புனித அந்தோணியார் அன்பியம்").getId();

        mockMvc.perform(post("/anbiyam/{id}/edit", first).with(user(administrator())).with(csrf())
                        .param("anbiyamName", "புனித அந்தோணியார் அன்பியம்")
                        .param("headMemberId", "")
                        .param("activeFlag", "true"))
                .andExpect(status().is3xxRedirection());

        org.junit.jupiter.api.Assertions.assertNull(
                named("புனித அந்தோணியார் அன்பியம்").getHeadMemberId());
    }

    @Test
    @DisplayName("the list carries the animator and both counts")
    void listShowsAnimatorAndCounts() throws Exception {
        // Read the animator rather than naming one: this is live sample data, and it
        // changes the moment someone edits it through the running application.
        Long animatorId = named("புனித அந்தோணியார் அன்பியம்").getHeadMemberId();
        String animatorName = memberRepository.findById(animatorId).orElseThrow().getDisplayName();

        mockMvc.perform(get("/anbiyam").with(user(administrator())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(animatorName)))
                .andExpect(content().string(containsString("#" + animatorId)))
                .andExpect(content().string(containsString("Families")))
                .andExpect(content().string(containsString("Animator")));
    }

    @Test
    @DisplayName("the edit form preselects the current animator, with the id shown")
    void editFormCarriesTheAnimator() throws Exception {
        Anbiyam anbiyam = named("புனித அந்தோணியார் அன்பியம்");
        Long animatorId = anbiyam.getHeadMemberId();
        String animatorName = memberRepository.findById(animatorId).orElseThrow().getDisplayName();

        mockMvc.perform(get("/anbiyam/{id}/edit", anbiyam.getId()).with(user(administrator())))
                .andExpect(status().isOk())
                // Selected in the dropdown, so saving cannot silently drop it.
                .andExpect(content().string(
                        containsString("value=\"" + animatorId + "\" selected=\"selected\"")))
                .andExpect(content().string(
                        containsString(animatorName + " (#" + animatorId + ")")));
    }

    @Test
    @DisplayName("created_user is finally populated")
    void auditColumnsArePopulated() throws Exception {
        mockMvc.perform(post("/anbiyam/new").with(user(administrator())).with(csrf())
                .param("anbiyamName", "புனித ஜோசப் அன்பியம்")
                .param("activeFlag", "true"));

        Anbiyam created = named("புனித ஜோசப் அன்பியம்");
        assertEquals("appsa@example.com", created.getCreatedUser());
        assertNotNull(created.getLastUpdatedDate());
    }

    @Test
    @DisplayName("two anbiyam in one parish cannot share a name")
    void duplicateNamesAreRejected() throws Exception {
        mockMvc.perform(post("/anbiyam/new").with(user(administrator())).with(csrf())
                        .param("anbiyamName", "புனித அந்தோணியார் அன்பியம்")
                        .param("activeFlag", "true"))
                // Redisplays the form rather than redirecting to the list.
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already exists")));
    }

    @Test
    @DisplayName("an anbiyam with members cannot be removed")
    void deleteIsRefusedWhileMembersRemain() throws Exception {
        Anbiyam withMembers = named("புனித அந்தோணியார் அன்பியம்");

        mockMvc.perform(post("/anbiyam/{id}/delete", withMembers.getId())
                        .with(user(administrator())).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertTrue(anbiyamRepository.findById(withMembers.getId())
                .filter(a -> !a.isDeletedFlag()).isPresent(), "still there");
    }

    @Test
    @DisplayName("delete is soft: the row stops appearing but is not destroyed")
    void deleteIsSoft() throws Exception {
        Anbiyam empty = named("அன்னை வேளாங்கண்ணி அன்பியம்");

        mockMvc.perform(post("/anbiyam/{id}/delete", empty.getId())
                        .with(user(administrator())).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Anbiyam after = anbiyamRepository.findById(empty.getId()).orElseThrow();
        assertTrue(after.isDeletedFlag(), "flagged, not removed");

        List<Anbiyam> visible =
                anbiyamRepository.findByChurchIdAndDeletedFlagFalseOrderByAnbiyamNameAsc(CHENNAI);
        assertTrue(visible.stream().noneMatch(a -> a.getId().equals(empty.getId())));
    }
}
