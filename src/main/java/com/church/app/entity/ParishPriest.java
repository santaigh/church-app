package com.church.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * One posting of one member of clergy to one parish.
 *
 * <p>An appointment history, not a current-state table: a priest who moves parish leaves
 * a closed row behind him and opens a new one at his next church. The table name is
 * {@code parish_priest} for history's sake, but it holds assistants and brothers too --
 * see {@link ClergyRole}.
 *
 * <p><b>{@code toDate == null} means currently serving.</b> That is the only definition.
 * The table used to carry an {@code active_flag} answering the same question, which is
 * how two columns end up disagreeing with nothing to catch it; V19 dropped it.
 *
 * <p>Exactly one {@code PARISH_PRIEST} may be open per church. MySQL has no partial
 * unique index, so that rule lives in the service, along with closing the previous
 * incumbent when a new one is appointed.
 */
@Entity
@Table(name = "parish_priest")
@Filter(name = TenantFilters.TENANT_FILTER, condition = TenantFilters.CHURCH_CONDITION)
@Getter
@Setter
public class ParishPriest extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    /**
     * Always a station. A substation holds no clergy of its own -- the station's priest
     * looks after it.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "church_id", nullable = false)
    private Church church;

    @Enumerated(EnumType.STRING)
    @Column(name = "clergy_role", nullable = false, length = 30)
    private ClergyRole clergyRole = ClergyRole.PARISH_PRIEST;

    /** Carries the honorific, as a parish writes it: {@code Fr. Antony Raj}. */
    @Column(name = "priest_name", nullable = false, length = 100)
    private String priestName;

    /** Where he served before this posting; blank for a first appointment. */
    @Column(name = "priest_last_place", length = 100)
    private String priestLastPlace;

    @Column(name = "from_date", nullable = false)
    private LocalDateTime fromDate;

    /** Null while serving; set to the day the posting ended on a move or retirement. */
    @Column(name = "to_date")
    private LocalDateTime toDate;

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isCurrentlyServing() {
        return toDate == null;
    }
}
