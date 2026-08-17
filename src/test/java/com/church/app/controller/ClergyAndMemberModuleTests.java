package com.church.app.controller;

import com.church.app.entity.ActorType;
import com.church.app.entity.ClergyRole;
import com.church.app.entity.Family;
import com.church.app.entity.FamilyRole;
import com.church.app.entity.Member;
import com.church.app.entity.ParishPriest;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.MemberRepository;
import com.church.app.repository.ParishPriestRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The clergy and member screens.
 *
 * <p>Transactional, so nothing these tests write survives -- other tests assert on the
 * sample data in {@code churchnew}, and a stray member would change their counts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClergyAndMemberModuleTests {

    private static final Long CHENNAI = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParishPriestRepository parishPriestRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private com.church.app.repository.RoleRepository roleRepository;

    @Autowired
    private com.church.app.repository.RolePermissionRepository rolePermissionRepository;

    @Autowired
    private com.church.app.repository.MemberExtRepository memberExtRepository;

    private static AppUserPrincipal principal(String role, ActorType type, Long churchId,
                                              String... permissions) {
        AppUserPrincipal.Builder builder = AppUserPrincipal.builder()
                .userId(99L)
                .churchId(churchId)
                .churchName(churchId == null ? null : "St. Mary's Cathedral")
                .actorType(type)
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

    /** Parish administrator: full rights over members, VIEW only over clergy. */
    private static AppUserPrincipal parishAdmin() {
        return principal("AppSA", ActorType.MEMBER, CHENNAI,
                "PERM_DASHBOARD_VIEW", "PERM_PARISH_PRIEST_VIEW",
                "PERM_MEMBER_VIEW", "PERM_MEMBER_ADD", "PERM_MEMBER_EDIT", "PERM_MEMBER_DELETE");
    }

    /** Platform admin working inside a parish: may appoint clergy. */
    private static AppUserPrincipal platformAdmin() {
        return principal("SaaSSAdmin", ActorType.MEMBER, CHENNAI,
                "PERM_DASHBOARD_VIEW", "PERM_PARISH_PRIEST_VIEW", "PERM_PARISH_PRIEST_ADD",
                "PERM_PARISH_PRIEST_EDIT", "PERM_PARISH_PRIEST_DELETE", "PERM_MEMBER_VIEW");
    }

    // ------------------------------------------------------------ parish priest

    @Test
    @DisplayName("parish staff see the clergy list but cannot reach the appoint screen")
    void appointingIsReservedToThePlatform() throws Exception {
        mockMvc.perform(get("/parish-priest").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fr. Antony Raj")));

        mockMvc.perform(get("/parish-priest/new").with(user(parishAdmin())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the history marks the open posting as current and shows no end date")
    void openPostingReadsAsCurrent() throws Exception {
        mockMvc.perform(get("/parish-priest").with(user(platformAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Current")))
                .andExpect(content().string(containsString("Fr. Gnanaprakasam")));
    }

    @Test
    @DisplayName("appointing a new parish priest closes the previous one automatically")
    void appointingClosesThePredecessor() throws Exception {
        ParishPriest before = currentParishPriest();

        mockMvc.perform(post("/parish-priest/new").with(user(platformAdmin())).with(csrf())
                        .param("clergyRole", "PARISH_PRIEST")
                        .param("priestName", "Fr. Newcomer")
                        .param("fromDate", "2026-06-01"))
                .andExpect(status().is3xxRedirection());

        ParishPriest closed = parishPriestRepository.findById(before.getId()).orElseThrow();
        assertEquals(2026, closed.getToDate().getYear(), "the predecessor was closed");
        assertEquals("Fr. Newcomer", currentParishPriest().getPriestName());
    }

    @Test
    @DisplayName("an assistant does not close the parish priest")
    void assistantsDoNotClosePriests() throws Exception {
        ParishPriest before = currentParishPriest();

        mockMvc.perform(post("/parish-priest/new").with(user(platformAdmin())).with(csrf())
                        .param("clergyRole", "ASSISTANT_PRIEST")
                        .param("priestName", "Fr. Helper")
                        .param("fromDate", "2026-06-01"))
                .andExpect(status().is3xxRedirection());

        // Several assistants may serve at once, so nothing was closed.
        assertEquals(before.getId(), currentParishPriest().getId());
        assertNull(currentParishPriest().getToDate());
    }

    @Test
    @DisplayName("an end date before the start date is refused")
    void backwardsDatesAreRefused() throws Exception {
        mockMvc.perform(post("/parish-priest/new").with(user(platformAdmin())).with(csrf())
                        .param("clergyRole", "BROTHER")
                        .param("priestName", "Br. Selvam")
                        .param("fromDate", "2026-06-01")
                        .param("toDate", "2025-01-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cannot be before")));
    }

    private ParishPriest currentParishPriest() {
        return parishPriestRepository
                .findFirstByChurchIdAndClergyRoleAndToDateIsNullAndDeletedFlagFalse(
                        CHENNAI, ClergyRole.PARISH_PRIEST)
                .orElseThrow();
    }

    // ------------------------------------------------------------------ members

    @Test
    @DisplayName("the member list links each person to their own page")
    void listLinksToEachMember() throws Exception {
        mockMvc.perform(get("/members").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Antony Raj")))
                .andExpect(content().string(containsString("/members/1")));
    }

    @Test
    @DisplayName("clicking a family narrows the list to that family alone")
    void listCanBeNarrowedToOneFamily() throws Exception {
        String page = mockMvc.perform(get("/members").param("family", "1")
                        .with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Showing")))
                .andReturn().getResponse().getContentAsString();

        // Family 1 holds Antony, Mary and Joseph; Stephen is in family 2.
        assertTrue(page.contains("Antony Raj"));
        assertTrue(!page.contains("Stephen Devasagayam"), "another family's members are gone");
    }

    @Test
    @DisplayName("a filtered list offers a way back, since a new tab has no history")
    void filteredListsCarryABackControl() throws Exception {
        // Filtered by family: back to everyone.
        mockMvc.perform(get("/members").param("family", "1").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Back to all members")));

        // Filtered by anbiyam: back to where that link came from.
        mockMvc.perform(get("/members").param("anbiyam", "1").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Back to Anbiyam")))
                .andExpect(content().string(containsString("/anbiyam")));

        // Unfiltered: nothing to go back from.
        mockMvc.perform(get("/members").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("Back to all members"))));
    }

    @Test
    @DisplayName("clicking an anbiyam shows only its members")
    void listCanBeNarrowedToOneAnbiyam() throws Exception {
        String page = mockMvc.perform(get("/members").param("anbiyam", "2")
                        .with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Anbiyam 2 has Peter alone.
        assertTrue(page.contains("Peter Fernando"));
        assertTrue(!page.contains("Antony Raj"), "members of the first anbiyam are gone");
    }

    @Test
    @DisplayName("a family reads in household order: head, spouse, then children")
    void familyMembersReadInHouseholdOrder() throws Exception {
        String page = mockMvc.perform(get("/members").param("family", "1")
                        .with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Antony heads that family, Mary is the spouse, Joseph the child. Alphabetically
        // Antony, Joseph, Mary -- so this passing means role order beat name order.
        int head = page.indexOf("Antony Raj");
        int spouse = page.indexOf("Mary Arulraj");
        int child = page.indexOf("Joseph Arulraj");
        assertTrue(head < spouse && spouse < child,
                "expected head before spouse before child, got " + head + "/" + spouse + "/" + child);
    }

    @Test
    @DisplayName("dates read day-first, never in ISO order")
    void datesAreDayFirst() throws Exception {
        // Fr. Gnanaprakasam served from 1 June 2016.
        mockMvc.perform(get("/parish-priest").with(user(platformAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("01-06-2016")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("2016-06-01"))));
    }

    @Test
    @DisplayName("a missing date shows as absent rather than blank")
    void missingDatesAreMarked() throws Exception {
        // Member 2 has a member_ext row but no sacrament dates recorded in it.
        mockMvc.perform(get("/members/2").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("—")));
    }

    @Test
    @DisplayName("every list names its actions column rather than leaving it blank")
    void actionsColumnIsLabelled() throws Exception {
        AppUserPrincipal reader = principal("AppSA", ActorType.MEMBER, CHENNAI,
                "PERM_DASHBOARD_VIEW", "PERM_MEMBER_VIEW", "PERM_ANBIYAM_VIEW",
                "PERM_PARISH_PRIEST_VIEW");

        for (String path : List.of("/members", "/anbiyam", "/parish-priest")) {
            mockMvc.perform(get(path).with(user(reader)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Operations")));
        }
    }

    @Test
    @DisplayName("every list carries per-column search, server-side where it can grow")
    void listsAreSearchable() throws Exception {
        AppUserPrincipal reader = principal("AppSA", ActorType.MEMBER, CHENNAI,
                "PERM_DASHBOARD_VIEW", "PERM_MEMBER_VIEW", "PERM_ANBIYAM_VIEW",
                "PERM_PARISH_PRIEST_VIEW");

        // Members can run to thousands, so its search is a form posted to the server.
        mockMvc.perform(get("/members").with(user(reader)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"member-search\"")));

        // Anbiyam and clergy are small by nature -- a parish has a handful of each -- so
        // they keep filtering in the browser, where it costs no round trip.
        for (String path : List.of("/anbiyam", "/parish-priest")) {
            mockMvc.perform(get(path).with(user(reader)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-filterable")));
        }
    }

    @Test
    @DisplayName("the member page links to the rest of the record")
    void memberPageLinksToExtraDetail() throws Exception {
        mockMvc.perform(get("/members/2").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/members/2/details")));
    }

    @Test
    @DisplayName("the member page carries the additional details inline, not a page away")
    void memberPageShowsMemberExtInline() throws Exception {
        // Member 2 has a member_ext row: occupation School Teacher.
        mockMvc.perform(get("/members/2").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("School Teacher")))
                .andExpect(content().string(containsString("Sacraments")))
                .andExpect(content().string(containsString("id=\"additional\"")));
    }

    @Test
    @DisplayName("a member with no extra record says so rather than showing blanks")
    void missingExtraDetailIsStated() throws Exception {
        mockMvc.perform(get("/members/1").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No additional details")));
    }

    @Test
    @DisplayName("the old details URL still lands somewhere sensible")
    void oldDetailsUrlRedirectsToTheSection() throws Exception {
        mockMvc.perform(get("/members/2/details").with(user(parishAdmin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/members/2#additional"));
    }

    @Test
    @DisplayName("additional details can be filled in for a member who has none")
    void extraDetailIsCreatedOnFirstSave() throws Exception {
        // Member 1 has no member_ext row at all, so this has to insert rather than update.
        mockMvc.perform(post("/members/1/details/edit").with(user(parishAdmin())).with(csrf())
                        .param("bloodGroup", "O+")
                        .param("occupation", "Carpenter")
                        .param("baptismDate", "1968-05-01")
                        .param("baptismPlace", "Santhome"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/members/1").with(user(parishAdmin())))
                .andExpect(content().string(containsString("Carpenter")))
                // Day-first on the way back out.
                .andExpect(content().string(containsString("01-05-1968")));
    }

    @Test
    @DisplayName("a read-only account cannot edit additional details")
    void extraDetailEditNeedsPermission() throws Exception {
        AppUserPrincipal readOnly = principal("AppUser", ActorType.MEMBER, CHENNAI,
                "PERM_DASHBOARD_VIEW", "PERM_MEMBER_VIEW");

        mockMvc.perform(get("/members/1/details/edit").with(user(readOnly)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("links stay in the same tab, leaving the choice to whoever clicks")
    void listLinksStayInTheSameTab() throws Exception {
        // Forcing new tabs piled one up per click, and is worse again on the phones and
        // tablets the payment screens are built for. Ctrl+click still opens a new tab
        // when someone actually wants one, and the Back control covers losing your place.
        mockMvc.perform(get("/members").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("target=\"_blank\""))));

        mockMvc.perform(get("/anbiyam").with(user(principal("AppSA", ActorType.MEMBER, CHENNAI,
                        "PERM_DASHBOARD_VIEW", "PERM_ANBIYAM_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("target=\"_blank\""))));
    }

    @Test
    @DisplayName("a new member is stamped with the parish and gets an account")
    void newMemberGetsChurchAndCredentials() throws Exception {
        mockMvc.perform(post("/members/new").with(user(parishAdmin())).with(csrf())
                        .param("firstName", "Testowicz")
                        .param("gender", "MALE")
                        .param("familyId", "1")
                        .param("anbiyamId", "1")
                        // Ignored: the form has no church field at all.
                        .param("churchId", "2"))
                .andExpect(status().is3xxRedirection());

        Member created = memberRepository.findByChurchIdAndDeletedFlagFalse(CHENNAI).stream()
                .filter(m -> "Testowicz".equals(m.getFirstName()))
                .findFirst().orElseThrow();

        assertEquals(CHENNAI, created.getChurch().getId());
        assertEquals("AppUser", created.getRole().getRoleCode());
        // Credentials live on member, so a new member is a new account -- on the default
        // password, forced to change it at first sign-in.
        assertTrue(created.isUsingDefaultPassword());
    }

    // ------------------------------------------------------------ role assignment

    @Test
    @DisplayName("an AppAdmin is not offered the super-admin role, and is refused it if posted")
    void appAdminCannotCreateASuperAdmin() throws Exception {
        AppUserPrincipal appAdmin = principal("AppAdmin", ActorType.MEMBER, CHENNAI,
                "PERM_DASHBOARD_VIEW", "PERM_MEMBER_VIEW", "PERM_MEMBER_ADD", "PERM_MEMBER_EDIT");

        // Not in the dropdown...
        mockMvc.perform(get("/members/new").with(user(appAdmin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("(AppSA)"))));

        // ...and a posted id is not the dropdown.
        Long superAdmin = roleRepository.findByRoleCode("AppSA").orElseThrow().getId();
        mockMvc.perform(post("/members/new").with(user(appAdmin)).with(csrf())
                        .param("firstName", "Climber")
                        .param("gender", "MALE")
                        .param("familyId", "1")
                        .param("anbiyamId", "1")
                        .param("roleId", String.valueOf(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("may not grant")));
    }

    @Test
    @DisplayName("an AppSA may hand out any parish role")
    void superAdminMayGrantAnything() throws Exception {
        AppUserPrincipal appSA = principal("AppSA", ActorType.MEMBER, CHENNAI,
                "PERM_DASHBOARD_VIEW", "PERM_MEMBER_VIEW", "PERM_MEMBER_ADD");

        mockMvc.perform(get("/members/new").with(user(appSA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("(AppSA)")));
    }

    @Test
    @DisplayName("nobody edits their own role")
    void ownRoleCannotBeChanged() throws Exception {
        // Signed in as member 2, editing member 2.
        AppUserPrincipal self = AppUserPrincipal.builder()
                .userId(2L).churchId(CHENNAI).churchName("St. Mary's Cathedral")
                .actorType(ActorType.MEMBER).username("self@example.com").password("x")
                .displayName("Self").role("AppSA")
                .usingDefaultPassword(false).locked(false).active(true)
                .permission("PERM_MEMBER_VIEW").permission("PERM_MEMBER_EDIT").build();

        // Deliberately a role member 2 does not already hold -- posting the one they have
        // is not a change, and there is nothing to refuse.
        String current = memberRepository.findById(2L).orElseThrow().getRole().getRoleCode();
        Long different = roleRepository.findByRoleLevelAndDeletedFlagFalse(
                        com.church.app.entity.RoleLevel.APP).stream()
                .filter(role -> !role.getRoleCode().equals(current))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/members/{id}/edit", 2L).with(user(self)).with(csrf())
                        .param("firstName", "Mary")
                        .param("gender", "FEMALE")
                        .param("familyId", "1")
                        .param("anbiyamId", "1")
                        .param("roleId", String.valueOf(different)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cannot change your own role")));
    }

    @Test
    @DisplayName("an ordinary parish account may collect a payment but not edit one")
    void appUserCanCollectPaymentsOnly() {
        Long appUser = roleRepository.findByRoleCode("AppUser").orElseThrow().getId();
        var operations = rolePermissionRepository
                .findByRoleIdAndResource(appUser, com.church.app.entity.Resource.PAYMENT)
                .stream()
                .map(com.church.app.entity.RolePermission::getOperation)
                .toList();

        assertTrue(operations.contains(com.church.app.entity.Operation.ADD), "may collect");
        assertTrue(operations.contains(com.church.app.entity.Operation.VIEW), "may see");
        assertTrue(!operations.contains(com.church.app.entity.Operation.EDIT),
                "whoever takes the cash cannot alter the record of it");
        assertTrue(!operations.contains(com.church.app.entity.Operation.DELETE));
    }

    // ------------------------------------------------- additional details on add

    @Test
    @DisplayName("the add screen carries additional details, folded away")
    void addScreenHasCollapsedExtraSection() throws Exception {
        mockMvc.perform(get("/members/new").with(user(parishAdmin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"collapse\"")))
                .andExpect(content().string(containsString("extra.bloodGroup")));
    }

    @Test
    @DisplayName("leaving the folded section empty writes no member_ext row at all")
    void emptyExtraSectionWritesNothing() throws Exception {
        mockMvc.perform(post("/members/new").with(user(parishAdmin())).with(csrf())
                        .param("firstName", "Plainly")
                        .param("gender", "MALE")
                        .param("familyId", "1")
                        .param("anbiyamId", "1"))
                .andExpect(status().is3xxRedirection());

        Member created = memberRepository.findByChurchIdAndDeletedFlagFalse(CHENNAI).stream()
                .filter(m -> "Plainly".equals(m.getFirstName())).findFirst().orElseThrow();
        assertTrue(memberExtRepository.findByMemberIdAndDeletedFlagFalse(created.getId()).isEmpty(),
                "no empty extra row for a member who has no extra details");
    }

    @Test
    @DisplayName("filling the folded section on add creates the extra record with it")
    void filledExtraSectionIsSavedOnAdd() throws Exception {
        mockMvc.perform(post("/members/new").with(user(parishAdmin())).with(csrf())
                        .param("firstName", "Detailed")
                        .param("gender", "FEMALE")
                        .param("familyId", "1")
                        .param("anbiyamId", "1")
                        .param("extra.bloodGroup", "B+")
                        .param("extra.occupation", "Nurse"))
                .andExpect(status().is3xxRedirection());

        Member created = memberRepository.findByChurchIdAndDeletedFlagFalse(CHENNAI).stream()
                .filter(m -> "Detailed".equals(m.getFirstName())).findFirst().orElseThrow();
        assertEquals("Nurse", memberExtRepository
                .findByMemberIdAndDeletedFlagFalse(created.getId()).orElseThrow().getOccupation());
    }

    @Test
    @DisplayName("a family from another parish is refused")
    void familyMustBelongToThisParish() throws Exception {
        mockMvc.perform(post("/members/new").with(user(parishAdmin())).with(csrf())
                        .param("firstName", "Outsider")
                        .param("gender", "MALE")
                        // Family 4 belongs to St. Joseph's, Madurai.
                        .param("familyId", "4")
                        .param("anbiyamId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("not in this parish")));
    }

    @Test
    @DisplayName("naming a new head demotes the old one and moves the family pointer")
    void oneHeadPerFamily() throws Exception {
        Family family = familyRepository.findById(1L).orElseThrow();
        Long previousHead = family.getHeadMemberId();

        // Joseph, member 3, is a child in that family. Promote him.
        mockMvc.perform(post("/members/{id}/edit", 3L).with(user(parishAdmin())).with(csrf())
                        .param("firstName", "Joseph")
                        .param("gender", "MALE")
                        .param("familyId", "1")
                        .param("anbiyamId", "1")
                        .param("familyRole", "HEAD"))
                .andExpect(status().is3xxRedirection());

        assertEquals(FamilyRole.HEAD, memberRepository.findById(3L).orElseThrow().getFamilyRole());
        assertEquals(3L, familyRepository.findById(1L).orElseThrow().getHeadMemberId(),
                "family.head_member_id follows");
        assertNull(memberRepository.findById(previousHead).orElseThrow().getFamilyRole(),
                "the previous head stands down rather than leaving two heads");

        List<Member> heads = memberRepository.findByChurchIdAndDeletedFlagFalse(CHENNAI).stream()
                .filter(m -> m.getFamily().getId().equals(1L))
                .filter(m -> m.getFamilyRole() == FamilyRole.HEAD)
                .toList();
        assertEquals(1, heads.size(), "exactly one head remains");
    }

    @Test
    @DisplayName("removing a member clears them as head of their family")
    void deletingAHeadClearsThePointer() throws Exception {
        Long head = familyRepository.findById(1L).orElseThrow().getHeadMemberId();

        mockMvc.perform(post("/members/{id}/delete", head).with(user(parishAdmin())).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertTrue(memberRepository.findById(head).orElseThrow().isDeletedFlag());
        assertNull(familyRepository.findById(1L).orElseThrow().getHeadMemberId(),
                "a deleted member must not stay on record as head");
    }
}
