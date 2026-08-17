package com.church.app.dto;

import com.church.app.entity.FamilyRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * What the member screen submits.
 *
 * <p>No church field: the parish comes from the tenant scope. The family and anbiyam are
 * chosen, but both are re-checked on save against that same scope -- a dropdown limiting
 * the options is presentation, not a guarantee.
 */
@Getter
@Setter
public class MemberForm {

    private Long id;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name may be at most 100 characters")
    private String firstName;

    @Size(max = 100, message = "Middle name may be at most 100 characters")
    private String middleName;

    @Size(max = 100, message = "Last name may be at most 100 characters")
    private String lastName;

    @NotBlank(message = "Gender is required")
    private String gender;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    /** Normalised to +91 form on save, so 9840100001 and +919840100001 are one number. */
    @Size(max = 20, message = "Mobile may be at most 20 characters")
    private String mobile;

    @Size(max = 20, message = "Alternate mobile may be at most 20 characters")
    private String alternateMobile;

    @Email(message = "Enter a valid email address")
    @Size(max = 150, message = "Email may be at most 150 characters")
    private String email;

    @NotNull(message = "Choose a family")
    private Long familyId;

    @NotNull(message = "Choose an anbiyam")
    private Long anbiyamId;

    /** Nullable: a member's position in the family may not be known yet. */
    private FamilyRole familyRole;

    @Size(max = 500, message = "Remarks may be at most 500 characters")
    private String remarks;

    /**
     * Which parish role this member's account holds.
     *
     * <p>The most security-sensitive field on the record, because credentials live on
     * {@code member}: granting a role is granting a way in. Checked in the service, not
     * just constrained by the dropdown.
     *
     * <p>Not annotated {@code @NotNull}: the screen marks it required, but omitting it
     * means AppUser rather than a validation error. A bulk import of two thousand
     * parishioners should not have to state "ordinary member" two thousand times, and
     * the safe default is the least privileged role.
     */
    private Long roleId;

    /**
     * The rest of the record, filled in on the same screen when the details happen to be
     * to hand. Left empty it writes nothing at all -- most members have no extra row.
     */
    @jakarta.validation.Valid
    private MemberExtForm extra = new MemberExtForm();
}
