package com.church.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

/**
 * The rest of a member's record: everything the parish keeps but rarely reads.
 *
 * <p>Split from {@link Member} because that table is read on every sign-in, and there is
 * no reason to drag baptism places and blood groups through an authentication query. One
 * row per member at most -- V16 added the unique key that guarantees it.
 *
 * <p>Ids are mapped as plain values rather than relations: this entity is read on its own
 * screen and never navigated through.
 */
@Entity
@Table(name = "member_ext")
@Filter(name = TenantFilters.TENANT_FILTER, condition = TenantFilters.CHURCH_CONDITION)
@Getter
@Setter
public class MemberExt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36, updatable = false)
    private String uuid;

    @Column(name = "church_id", nullable = false)
    private Long churchId;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

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

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "education", length = 100)
    private String education;

    @Column(name = "native_place", length = 150)
    private String nativePlace;

    @Column(name = "baptism_date")
    private LocalDate baptismDate;

    @Column(name = "baptism_place", length = 150)
    private String baptismPlace;

    @Column(name = "holy_communion_date")
    private LocalDate holyCommunionDate;

    @Column(name = "holy_communion_place", length = 150)
    private String holyCommunionPlace;

    @Column(name = "confirmation_date")
    private LocalDate confirmationDate;

    @Column(name = "confirmation_place", length = 150)
    private String confirmationPlace;

    @Column(name = "marriage_date")
    private LocalDate marriageDate;

    @Column(name = "marriage_place", length = 150)
    private String marriagePlace;

    @Column(name = "record_status", nullable = false, length = 20)
    private String recordStatus = "ACTIVE";

    @Column(name = "deleted_flag", nullable = false)
    private boolean deletedFlag;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
