package com.church.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The receipt counter for one parish in one year.
 *
 * <p>Deliberately not {@code AUTO_INCREMENT}: that is global rather than per-church and
 * leaves gaps whenever a transaction rolls back. A parish auditor asking why receipt 41
 * does not exist is a conversation worth avoiding.
 *
 * <p>Rows are read with a pessimistic lock while a receipt is issued, so two secretaries
 * saving at the same moment cannot be given the same number.
 */
@Entity
@Table(name = "receipt_sequence")
@Getter
@Setter
public class ReceiptSequence extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "church_id", nullable = false)
    private Church church;

    @Column(name = "sequence_year", nullable = false)
    private Short sequenceYear;

    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix = "R";

    @Column(name = "last_number", nullable = false)
    private Integer lastNumber = 0;

    /** Advances the counter and returns the formatted number, e.g. {@code R-2026-0042}. */
    public String nextReceiptNo() {
        lastNumber = lastNumber + 1;
        return "%s-%d-%04d".formatted(prefix, sequenceYear, lastNumber);
    }
}
