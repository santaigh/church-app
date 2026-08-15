package com.church.app.repository;

import com.church.app.entity.ClergyRole;
import com.church.app.entity.ParishPriest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParishPriestRepository extends JpaRepository<ParishPriest, Long> {

    /**
     * Everyone serving this parish right now -- priest, assistants and brothers.
     *
     * <p>An open row is a current one: {@code to_date IS NULL}. Ordered by role so the
     * parish priest heads the list.
     */
    List<ParishPriest> findByChurchIdAndToDateIsNullAndDeletedFlagFalseOrderByClergyRoleAscFromDateAsc(
            Long churchId);

    /**
     * The parish priest currently serving, if there is one.
     *
     * <p>Optional rather than a plain result because a gap between postings is allowed:
     * a parish with no priest recorded shows a warning rather than failing.
     */
    Optional<ParishPriest> findFirstByChurchIdAndClergyRoleAndToDateIsNullAndDeletedFlagFalse(
            Long churchId, ClergyRole clergyRole);

    /** The full posting history for a parish, most recent appointment first. */
    List<ParishPriest> findByChurchIdAndDeletedFlagFalseOrderByFromDateDesc(Long churchId);

    long countByChurchIdAndClergyRoleAndToDateIsNullAndDeletedFlagFalse(Long churchId,
                                                                       ClergyRole clergyRole);
}
