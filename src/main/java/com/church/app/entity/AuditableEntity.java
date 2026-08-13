package com.church.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The four audit columns every table in this schema carries.
 *
 * <p>Only these four are shared. {@code record_status}, {@code deleted_flag} and
 * {@code version} are declared per entity instead, because they are not universal --
 * {@code anbiyam} has no {@code version} column and {@code parish_priest} has neither
 * {@code record_status} nor {@code deleted_flag}.
 *
 * <p>{@code create_date} is left to the database default and mapped read-only.
 * {@code last_updated_date} has no {@code ON UPDATE} clause in this schema, so the
 * application is responsible for setting it.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity {

    @Column(name = "create_date", insertable = false, updatable = false)
    private LocalDateTime createDate;

    @Column(name = "created_user", length = 100)
    private String createdUser;

    @Column(name = "last_updated_date")
    private LocalDateTime lastUpdatedDate;

    @Column(name = "updated_user", length = 100)
    private String updatedUser;
}
