package com.church.app.repository;

import com.church.app.entity.Anbiyam;
import com.church.app.entity.Member;
import com.church.app.entity.Role;
import com.church.app.entity.RoleLevel;
import com.church.app.entity.SaasUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the mappings against the real schema.
 *
 * <p>Startup already proves the columns line up, because {@code ddl-auto: validate}
 * refuses to boot on drift. These tests go further and prove the queries login will
 * depend on actually return what is expected.
 *
 * <p>Read-only: every assertion is against the seeded sample data, nothing is written.
 */
@SpringBootTest
@Transactional
class RepositoryMappingTests {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SaasUserRepository saasUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ChurchRepository churchRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private AnbiyamRepository anbiyamRepository;

    @Test
    @DisplayName("all six roles are seeded, split three SAAS and three APP")
    void rolesAreSeeded() {
        assertEquals(6, roleRepository.count());

        List<Role> saasRoles = roleRepository.findByRoleLevelAndDeletedFlagFalse(RoleLevel.SAAS);
        List<Role> appRoles = roleRepository.findByRoleLevelAndDeletedFlagFalse(RoleLevel.APP);
        assertEquals(3, saasRoles.size());
        assertEquals(3, appRoles.size());

        Role superAdmin = roleRepository.findByRoleCode("SaaSSAdmin").orElseThrow();
        assertEquals(RoleLevel.SAAS, superAdmin.getRoleLevel());
        assertTrue(superAdmin.isSystemDefined());
    }

    @Test
    @DisplayName("a member can be found by email or by mobile, and carries their role")
    void memberResolvesByEitherLoginIdentifier() {
        Member byEmail = memberRepository
                .findByEmailOrMobile("antony.raj@stmarys-chennai.org", null).orElseThrow();
        Member byMobile = memberRepository
                .findByEmailOrMobile(null, "+919840100001").orElseThrow();

        assertEquals(byEmail.getId(), byMobile.getId());
        assertEquals("AppSA", byEmail.getRole().getRoleCode());
        assertEquals("St. Mary's Cathedral", byEmail.getChurch().getChurchName());
        assertTrue(byEmail.canSignIn());

        // Deliberately asserts nothing about password_flag or locked_at. Those change
        // whenever anyone uses the running application -- signing in, changing a
        // password, or tripping the lockout -- so an assertion on them would fail for
        // reasons unrelated to what this test is about, which is that a member resolves
        // by either identifier and carries their role and church.
    }

    @Test
    @DisplayName("email lookup is case-insensitive")
    void emailLookupIgnoresCase() {
        assertTrue(memberRepository
                .findByEmailOrMobile("ANTONY.RAJ@STMARYS-CHENNAI.ORG", null).isPresent());
    }

    @Test
    @DisplayName("members with no email and no mobile cannot be resolved for login")
    void membersWithoutContactDetailsCannotSignIn() {
        Member joseph = memberRepository.findById(3L).orElseThrow();
        assertFalse(joseph.canSignIn());
        // A blank identifier must not accidentally match their NULL columns.
        assertEquals(Optional.empty(), memberRepository.findByEmailOrMobile("", null));
    }

    @Test
    @DisplayName("platform accounts resolve from saas_user and have no church")
    void saasUserResolves() {
        SaasUser admin = saasUserRepository
                .findByEmailOrMobile("superadmin@churchapp.local", null).orElseThrow();
        assertEquals("SaaSSAdmin", admin.getRole().getRoleCode());
        assertEquals(RoleLevel.SAAS, admin.getRole().getRoleLevel());
        assertTrue(admin.isUsingDefaultPassword());
        assertEquals(3, saasUserRepository.count());
    }

    @Test
    @DisplayName("tenant scoping: each church sees only its own members and families")
    void dataIsScopedPerChurch() {
        assertEquals(3, churchRepository.findByDeletedFlagFalseOrderByChurchNameAsc().size());

        assertEquals(6, memberRepository.findByChurchIdAndDeletedFlagFalse(1L).size());
        assertEquals(3, memberRepository.findByChurchIdAndDeletedFlagFalse(2L).size());
        assertEquals(2, memberRepository.findByChurchIdAndDeletedFlagFalse(3L).size());

        assertEquals(3, familyRepository.findByChurchIdAndDeletedFlagFalse(1L).size());
    }

    @Test
    @DisplayName("family codes are unique per church, not globally")
    void familyCodesRestartPerChurch() {
        // Both parishes have a FAM-001, and they are different families.
        Long chennai = familyRepository.findByChurchIdAndFamilyCode(1L, "FAM-001").orElseThrow().getId();
        Long madurai = familyRepository.findByChurchIdAndFamilyCode(2L, "FAM-001").orElseThrow().getId();
        assertNotNull(chennai);
        assertNotNull(madurai);
        assertFalse(chennai.equals(madurai));
    }

    @Test
    @DisplayName("Tamil Anbiyam names survive the round trip through MySQL")
    void tamilTextRoundTrips() {
        List<Anbiyam> anbiyams = anbiyamRepository
                .findByChurchIdAndDeletedFlagFalseOrderByAnbiyamNameAsc(1L);
        assertEquals(3, anbiyams.size());
        assertTrue(anbiyams.stream()
                        .anyMatch(a -> a.getAnbiyamName().equals("புனித அந்தோணியார் அன்பியம்")),
                "Tamil Anbiyam name did not round-trip; got: "
                        + anbiyams.stream().map(Anbiyam::getAnbiyamName).toList());
    }
}
