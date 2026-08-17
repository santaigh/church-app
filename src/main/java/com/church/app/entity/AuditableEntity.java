package com.church.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * The four audit columns every table in this schema carries.
 *
 * <p>Only these four are shared. {@code record_status}, {@code deleted_flag} and
 * {@code version} are declared per entity instead, because they are not universal --
 * {@code anbiyam} has no {@code version} column, for instance.
 *
 * <p>{@code create_date} is left to the database default and mapped read-only. The other
 * three are filled by Spring Data auditing, which asks {@code AuditorAwareImpl} who is
 * signed in. {@code last_updated_date} needs the listener because this schema has no
 * {@code ON UPDATE} clause -- the application has always been responsible for it.
 *
 * <p>Bulk {@code @Modifying} queries bypass the listener, by design: the lockout counters
 * on {@code member} are deliberate single-statement updates and are not user edits.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class AuditableEntity {

    @Column(name = "create_date", insertable = false, updatable = false)
    private LocalDateTime createDate;

    @CreatedBy
    @Column(name = "created_user", length = 100, updatable = false)
    private String createdUser;

    @LastModifiedDate
    @Column(name = "last_updated_date")
    private LocalDateTime lastUpdatedDate;

    @LastModifiedBy
    @Column(name = "updated_user", length = 100)
    private String updatedUser;
}
