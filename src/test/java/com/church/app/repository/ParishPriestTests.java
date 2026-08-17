package com.church.app.repository;

import com.church.app.entity.ClergyRole;
import com.church.app.entity.ParishPriest;
import com.church.app.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clergy history, now that {@code parish_priest} is finally mapped.
 *
 * <p>Read-only; nothing is written. The assertions are about V19's repair as much as the
 * mapping: the priests still serving had invented end dates because the column could not
 * be null, and those had to be cleared.
 */
@SpringBootTest
@Transactional
class ParishPriestTests {

    private static final Long CHENNAI = 1L;

    @Autowired
    private ParishPriestRepository parishPriestRepository;

    @BeforeEach
    void scopeToChennai() {
        TenantContext.setPlatformWide();
    }

    @AfterEach
    void clearScope() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a serving priest has no end date, and the invented ones are gone")
    void currentPostingsHaveNoEndDate() {
        List<ParishPriest> current = parishPriestRepository
                .findByChurchIdAndToDateIsNullAndDeletedFlagFalseOrderByClergyRoleAscFromDateAsc(CHENNAI);

        assertEquals(1, current.size(), "St. Mary's has one serving priest");
        ParishPriest priest = current.get(0);
        assertEquals("Fr. Antony Raj", priest.getPriestName());
        // Was 2027-05-31 before V19: a date nobody had decided, forced by NOT NULL.
        assertNull(priest.getToDate());
        assertTrue(priest.isCurrentlyServing());
    }

    @Test
    @DisplayName("a closed posting keeps the date the priest actually left")
    void endedPostingsKeepTheirDate() {
        // Found by name, not by position: appointments are added through the running
        // application, so neither the size of this history nor the order is fixed.
        ParishPriest previous =
                parishPriestRepository.findByChurchIdAndDeletedFlagFalseOrderByFromDateDesc(CHENNAI)
                        .stream()
                        .filter(posting -> "Fr. Gnanaprakasam".equals(posting.getPriestName()))
                        .findFirst()
                        .orElseThrow();

        assertEquals(2021, previous.getToDate().getYear());
        assertTrue(!previous.isCurrentlyServing());
    }

    @Test
    @DisplayName("an assistant serving alongside does not close the parish priest")
    void assistantsDoNotDisplaceThePriest() {
        List<ParishPriest> serving = parishPriestRepository
                .findByChurchIdAndToDateIsNullAndDeletedFlagFalseOrderByClergyRoleAscFromDateAsc(CHENNAI);

        // However many assistants or brothers are serving, exactly one parish priest is.
        assertEquals(1, serving.stream()
                .filter(p -> p.getClergyRole() == ClergyRole.PARISH_PRIEST)
                .count());
    }

    @Test
    @DisplayName("every seeded posting defaults to the parish priest role")
    void existingRowsAreParishPriests() {
        // V19 gave the new column a default, so the four rows that predate clergy roles
        // are all parish priests -- which is what they were.
        assertEquals(1, parishPriestRepository
                .countByChurchIdAndClergyRoleAndToDateIsNullAndDeletedFlagFalse(
                        CHENNAI, ClergyRole.PARISH_PRIEST));
        assertEquals(0, parishPriestRepository
                .countByChurchIdAndClergyRoleAndToDateIsNullAndDeletedFlagFalse(
                        CHENNAI, ClergyRole.BROTHER));
    }

    @Test
    @DisplayName("the current parish priest is reachable directly")
    void currentParishPriestResolves() {
        assertEquals("Fr. Antony Raj", parishPriestRepository
                .findFirstByChurchIdAndClergyRoleAndToDateIsNullAndDeletedFlagFalse(
                        CHENNAI, ClergyRole.PARISH_PRIEST)
                .orElseThrow()
                .getPriestName());
    }

    @Test
    @DisplayName("a substation has no clergy of its own")
    void substationsHaveNoClergy() {
        // St. Anthony's Chapel is church 4, under St. Mary's. Its priest is St. Mary's.
        assertTrue(parishPriestRepository
                .findByChurchIdAndToDateIsNullAndDeletedFlagFalseOrderByClergyRoleAscFromDateAsc(4L)
                .isEmpty());
    }
}
