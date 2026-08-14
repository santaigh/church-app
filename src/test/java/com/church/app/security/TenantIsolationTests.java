package com.church.app.security;

import com.church.app.entity.Member;
import com.church.app.repository.AnbiyamRepository;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.MemberRepository;
import com.church.app.repository.PaymentDueRepository;
import com.church.app.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves a parish cannot read another parish's data.
 *
 * <p>These assertions are the whole point of the tenant filter. Without them the
 * protection is the sort that silently stops working after an unrelated change and is
 * noticed only when someone reports seeing the wrong parish.
 *
 * <p>Sample data: church 1 (St. Mary's, Chennai) has 6 members, church 2 (St. Joseph's,
 * Madurai) has 3, church 3 (Our Lady of Lourdes, Trichy) has 2.
 */
@SpringBootTest
@Transactional
class TenantIsolationTests {

    private static final long CHENNAI = 1L;
    private static final long MADURAI = 2L;

    /** Xavier Britto — a Madurai member, invisible to Chennai. */
    private static final long MADURAI_MEMBER_ID = 7L;
    /** Antony Raj — a Chennai member. */
    private static final long CHENNAI_MEMBER_ID = 1L;

    @Autowired private MemberRepository memberRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private AnbiyamRepository anbiyamRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentDueRepository paymentDueRepository;

    @AfterEach
    void clearScope() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ list queries

    @Test
    @DisplayName("findAll returns only the current parish -- the leak this exists to stop")
    void findAllIsScopedToOneChurch() {
        TenantContext.setChurch(CHENNAI);
        List<Member> chennai = memberRepository.findAll();
        assertEquals(6, chennai.size(), "Chennai should see its own 6 members, not all 11");
        assertTrue(chennai.stream().allMatch(m -> m.getChurch().getId().equals(CHENNAI)));

        TenantContext.clear();
        TenantContext.setChurch(MADURAI);
        List<Member> madurai = memberRepository.findAll();
        assertEquals(3, madurai.size());
        assertTrue(madurai.stream().allMatch(m -> m.getChurch().getId().equals(MADURAI)));
    }

    @Test
    @DisplayName("count is scoped too, so totals on a dashboard cannot leak")
    void countIsScoped() {
        TenantContext.setChurch(CHENNAI);
        assertEquals(6, memberRepository.count());
        assertEquals(3, familyRepository.count());
        assertEquals(3, anbiyamRepository.count());
    }

    // ------------------------------------------------- direct id access (the IDOR case)

    @Test
    @DisplayName("findById cannot reach another parish -- /member/7 from Chennai")
    void findByIdCannotCrossTenants() {
        TenantContext.setChurch(CHENNAI);

        // Hibernate filters do NOT apply to primary-key loads. This passes only because
        // TenantAwareRepositoryImpl re-routes findById through a criteria query.
        Optional<Member> madurai = memberRepository.findById(MADURAI_MEMBER_ID);
        assertTrue(madurai.isEmpty(), "a Chennai user must not load a Madurai member by id");

        Optional<Member> own = memberRepository.findById(CHENNAI_MEMBER_ID);
        assertTrue(own.isPresent(), "a member of the same parish must still load");
        assertEquals("Antony Raj", own.get().getDisplayName());
    }

    @Test
    @DisplayName("existsById does not confirm the existence of another parish's record")
    void existsByIdCannotCrossTenants() {
        TenantContext.setChurch(CHENNAI);
        assertFalse(memberRepository.existsById(MADURAI_MEMBER_ID));
        assertTrue(memberRepository.existsById(CHENNAI_MEMBER_ID));
    }

    // ------------------------------------------------------------- financial records

    @Test
    @DisplayName("payments and dues are scoped -- one parish cannot read another's money")
    void financialRecordsAreScoped() {
        TenantContext.setChurch(CHENNAI);
        assertEquals(2, paymentRepository.count(), "Chennai issued 2 receipts");
        assertEquals(9, paymentDueRepository.count(), "3 families x 3 months");

        TenantContext.clear();
        TenantContext.setChurch(MADURAI);
        assertEquals(1, paymentRepository.count(), "Madurai issued 1 receipt");
        assertEquals(6, paymentDueRepository.count(), "2 families x 3 months");
    }

    @Test
    @DisplayName("derived queries are filtered, not just findAll")
    void derivedQueriesAreScoped() {
        TenantContext.setChurch(CHENNAI);
        // Family 4 belongs to Madurai. Even asking for it explicitly returns nothing.
        assertTrue(familyRepository.findById(4L).isEmpty());
        assertEquals(3, familyRepository.findByChurchIdAndDeletedFlagFalse(CHENNAI).size());
    }

    // ------------------------------------------------------------- platform accounts

    @Test
    @DisplayName("platform users deliberately see every parish")
    void platformScopeIsUnfiltered() {
        TenantContext.setPlatformWide();
        assertEquals(11, memberRepository.count(), "a SaaS user reaches all three churches");
        assertEquals(6, familyRepository.count());
        assertTrue(memberRepository.findById(MADURAI_MEMBER_ID).isPresent());
        assertTrue(memberRepository.findById(CHENNAI_MEMBER_ID).isPresent());
    }

    @Test
    @DisplayName("switching scope within a thread re-scopes subsequent queries")
    void scopeSwitchesCleanly() {
        TenantContext.setChurch(CHENNAI);
        assertEquals(6, memberRepository.count());

        TenantContext.clear();
        TenantContext.setChurch(MADURAI);
        assertEquals(3, memberRepository.count(),
                "a stale filter parameter would still report Chennai's 6");
    }
}
