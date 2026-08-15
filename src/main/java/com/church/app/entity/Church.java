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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A parish. The tenant root -- every church-scoped record hangs off this. */
@Entity
@Table(name = "church")
@Getter
@Setter
public class Church extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    @Column(name = "church_name", nullable = false, length = 150)
    private String churchName;

    /**
     * Self-reference: a substation points at its parent parish, and a station has no
     * parent. That is the whole definition -- there is no category column, because two
     * places recording the same fact had already disagreed with each other.
     *
     * <p>A substation is a place of worship, not an organisation. Members, families,
     * anbiyams and the parish priest all belong to the station; the station's priest
     * looks after its substations as well. Nothing tenant-scoped points at a substation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_church_id")
    private Church parentChurch;

    @Column(name = "parent_church_name")
    private String parentChurchName;

    @Column(name = "location")
    private String location;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "diocese", length = 150)
    private String diocese;

    /** Denormalised current priest name; the full posting history is in parish_priest. */
    @Column(name = "parish_priest", length = 150)
    private String parishPriest;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "country", length = 100)
    private String country = "India";

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "established_date")
    private LocalDate establishedDate;

    @Column(name = "record_status", nullable = false, length = 20)
    private String recordStatus = "ACTIVE";

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** A parish in its own right, as opposed to an outstation chapel under one. */
    public boolean isStation() {
        return parentChurch == null;
    }
}
