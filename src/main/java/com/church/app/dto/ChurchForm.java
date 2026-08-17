package com.church.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * A parish, as the platform creates and edits one.
 *
 * <p>Carries its first administrator on creation. A parish nobody can sign into is not
 * usable: adding a member needs a family and an anbiyam, and creating those needs someone
 * signed in — a deadlock that only the creating step can break.
 */
@Getter
@Setter
public class ChurchForm {

    private Long id;

    @NotBlank(message = "The parish needs a name")
    @Size(max = 150, message = "Name may be at most 150 characters")
    private String churchName;

    @Size(max = 150)
    private String diocese;

    @Size(max = 255)
    private String location;

    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    /** Shown beside the name everywhere, because two parishes may share one. */
    @NotBlank(message = "The town is needed — two parishes may share a name")
    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String district;

    @Size(max = 100)
    private String state;

    @Size(max = 100)
    private String country = "India";

    @Size(max = 10)
    private String pincode;

    @Size(max = 20)
    private String phone;

    @Email(message = "Enter a valid email address")
    @Size(max = 150)
    private String email;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate establishedDate;

    /** Only filled while creating. Ignored on edit — a parish has its own member screens. */
    @Valid
    private FirstAdministrator administrator = new FirstAdministrator();

    /**
     * The account that can first sign into a new parish.
     *
     * <p>Created as an AppSA on the default password with a forced change, exactly as any
     * other member is.
     */
    @Getter
    @Setter
    public static class FirstAdministrator {

        @Size(max = 100)
        private String firstName;

        @Size(max = 100)
        private String lastName;

        @Email(message = "Enter a valid email address")
        @Size(max = 150)
        private String email;

        @Size(max = 20)
        private String mobile;

        /** member.gender is NOT NULL, so it is asked for rather than assumed. */
        private String gender = "MALE";

        /** Nothing typed at all means "create no account yet". */
        public boolean isEmpty() {
            return blank(firstName) && blank(email) && blank(mobile);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
