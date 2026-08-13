package com.church.app.repository;

import com.church.app.entity.ReceiptSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptSequenceRepository extends JpaRepository<ReceiptSequence, Long> {

    Optional<ReceiptSequence> findByChurchIdAndSequenceYear(Long churchId, Short sequenceYear);

    /**
     * Takes a row lock while a receipt number is issued.
     *
     * <p>Without this, two secretaries saving a receipt at the same instant would both
     * read the same {@code lastNumber} and both produce the same receipt number -- one
     * of the two saves would then fail on the unique key, losing a payment entry.
     *
     * <p>Must be called inside a transaction; the lock is held until it commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM ReceiptSequence s
            WHERE s.church.id = :churchId AND s.sequenceYear = :year
            """)
    Optional<ReceiptSequence> findForUpdate(@Param("churchId") Long churchId, @Param("year") Short year);
}
