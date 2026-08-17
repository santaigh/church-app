package com.church.app.repository;

import com.church.app.entity.Family;
import com.church.app.entity.FamilyRole;
import com.church.app.entity.Member;
import com.church.app.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code member.family_role} after V21 renamed the column and V22 corrected its values.
 *
 * <p>The invariant worth guarding is the one that used to be impossible to state at all:
 * a family's head is named in two places -- {@code family.head_member_id} and the
 * member's own role -- and they must agree. Three earlier columns in this schema recorded
 * a fact twice and drifted apart; this test is what stops the fourth.
 */
@SpringBootTest
@Transactional
class FamilyRoleTests {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @BeforeEach
    void seeEveryChurch() {
        TenantContext.setPlatformWide();
    }

    @AfterEach
    void clearScope() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("no parish duty or occupation is left in the family role column")
    void legacyValuesAreGone() {
        List<Member> members = memberRepository.findAll();
        assertEquals(11, members.size());

        // PRIEST, SECRETARY, ANIMATOR, STUDENT, HOMEMAKER and TEACHER used to live here.
        // Being an enum now, anything else would have failed to load at all -- so the
        // real assertion is that every value present is a family position.
        assertTrue(members.stream()
                        .map(Member::getFamilyRole)
                        .filter(role -> role != null)
                        .allMatch(role -> List.of(FamilyRole.values()).contains(role)),
                "only family positions remain");
    }

    @Test
    @DisplayName("every family has exactly one head, and it is the member the family points at")
    void headMatchesTheFamilyPointer() {
        for (Family family : familyRepository.findAll()) {
            List<Member> members = memberRepository.findAll().stream()
                    .filter(m -> m.getFamily().getId().equals(family.getId()))
                    .filter(m -> !m.isDeletedFlag())
                    .toList();

            List<Member> heads = members.stream()
                    .filter(m -> m.getFamilyRole() == FamilyRole.HEAD)
                    .toList();

            assertEquals(1, heads.size(),
                    "family " + family.getFamilyCode() + " should have exactly one head");
            assertEquals(family.getHeadMemberId(), heads.get(0).getId(),
                    "the head's role and family.head_member_id must name the same person");
        }
    }

    @Test
    @DisplayName("the head is followed as recorded, even where it is the younger member")
    void headIsNotSecondGuessed() {
        // Family 6 is headed by Amala, born 1994, not by Sebastian, born 1975. A rule
        // based on age would have got this wrong; the data was followed as it stands.
        Member amala = memberRepository.findAll().stream()
                .filter(m -> "Amala".equals(m.getFirstName()))
                .findFirst().orElseThrow();
        assertEquals(FamilyRole.HEAD, amala.getFamilyRole());

        Member sebastian = memberRepository.findAll().stream()
                .filter(m -> "Sebastian".equals(m.getFirstName()))
                .findFirst().orElseThrow();
        assertEquals(FamilyRole.SPOUSE, sebastian.getFamilyRole());
    }

    @Test
    @DisplayName("the roles present are the ones these families actually have")
    void roleSpreadIsAsCurated() {
        Map<FamilyRole, Long> byRole = memberRepository.findAll().stream()
                .filter(m -> m.getFamilyRole() != null)
                .collect(Collectors.groupingBy(Member::getFamilyRole, Collectors.counting()));

        assertEquals(6L, byRole.get(FamilyRole.HEAD), "one per family");
        assertEquals(4L, byRole.get(FamilyRole.SPOUSE));
        assertEquals(1L, byRole.get(FamilyRole.CHILD));
    }

    @Test
    @DisplayName("the display order runs head, spouse, child, father, mother")
    void enumOrderIsTheDisplayOrder() {
        assertEquals(List.of(FamilyRole.HEAD, FamilyRole.SPOUSE, FamilyRole.CHILD,
                        FamilyRole.FATHER, FamilyRole.MOTHER),
                List.of(FamilyRole.values()));
        assertNotNull(FamilyRole.HEAD.getLabel());
    }
}
