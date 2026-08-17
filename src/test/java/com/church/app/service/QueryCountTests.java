package com.church.app.service;

import com.church.app.entity.ActorType;
import com.church.app.security.AppUserPrincipal;
import com.church.app.security.TenantContext;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against the slowest kind of bug: the one that works.
 *
 * <p>A list that queries once per row is correct, passes every other test, and is fine
 * against three families. At six hundred it is hundreds of round trips per page, and
 * nothing about the code says so. These tests count the statements actually issued, so a
 * lazy field added later fails here rather than in a parish.
 *
 * <p>The bounds are deliberately loose. The point is that a page costs a handful of
 * queries rather than a number that grows with the rows on it.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class QueryCountTests {

    private static final Long CHENNAI = 1L;

    @Autowired
    private MemberService memberService;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AnbiyamService anbiyamService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void signIn() {
        TenantContext.setChurch(CHENNAI);
        AppUserPrincipal principal = AppUserPrincipal.builder()
                .userId(4L).churchId(CHENNAI).churchName("St. Mary's Cathedral")
                .actorType(ActorType.MEMBER).username("admin@example.com").password("x")
                .displayName("Admin").role("AppSA")
                .usingDefaultPassword(false).locked(false).active(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private long statements() {
        return statistics.getPrepareStatementCount();
    }

    private void assertBounded(String screen, long before, int limit) {
        long used = statements() - before;
        assertTrue(used <= limit,
                screen + " issued " + used + " statements; expected no more than " + limit
                        + ". A per-row query has probably crept back in.");
    }

    @Test
    @DisplayName("the member list costs a fixed handful of queries, not one per row")
    void memberListDoesNotQueryPerRow() {
        long before = statements();
        var page = memberService.list(null, null, null, 1, 50);

        // Rows plus their family and anbiyam come back together: the search, its count,
        // and nothing per row. Without the JOIN FETCH this was two extra per member.
        assertBounded("Member list", before, 6);
        assertTrue(page.totalRows() > 0);
    }

    @Test
    @DisplayName("the family list counts members in one query, not one per family")
    void familyListDoesNotCountPerRow() {
        long before = statements();
        familyService.list(null, 1, 50);

        // Search, count, one GROUP BY for member counts, one lookup for the heads.
        assertBounded("Family list", before, 6);
    }

    @Test
    @DisplayName("choosing a family to collect from does not query dues per family")
    void collectionChooserDoesNotQueryPerFamily() {
        long before = statements();
        paymentService.familiesWithDues(null, 1, 50);

        // This was the worst of them: a dues lookup and a head lookup for every household
        // in the parish, on the screen a collector opens most often.
        assertBounded("Collection family chooser", before, 6);
    }

    @Test
    @DisplayName("the cutover screen loads every opening balance in one query")
    void openingBalancesDoNotQueryPerFamily() {
        long before = statements();
        paymentService.openingBalances(1, 50);

        assertBounded("Opening balances", before, 6);
    }

    @Test
    @DisplayName("the anbiyam list aggregates its counts")
    void anbiyamListAggregates() {
        long before = statements();
        anbiyamService.list();

        // Anbiyam, family counts, member counts, animators -- four, whatever the size.
        assertBounded("Anbiyam list", before, 6);
    }
}
