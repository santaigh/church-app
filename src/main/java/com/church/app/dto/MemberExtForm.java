package com.church.app.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * The rest of a member's record.
 *
 * <p>Its own form rather than fields appended to {@link MemberForm}: together they are
 * more than thirty inputs, and one screen of that is a wall nobody reads.
 *
 * <p>Everything is optional. A parish records what it knows, and a blank baptism place is
 * not an error.
 */
@Getter
@Setter
public class MemberExtForm {

    @Size(max = 5, message = "Blood group may be at most 5 characters")
    private String bloodGroup;

    @Size(max = 20)
    private String maritalStatus;

    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String district;

    @Size(max = 100)
    private String state;

    @Size(max = 10, message = "Pincode may be at most 10 characters")
    private String pincode;

    @Size(max = 100)
    private String occupation;

    @Size(max = 100)
    private String education;

    @Size(max = 150)
    private String nativePlace;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate baptismDate;

    @Size(max = 150)
    private String baptismPlace;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate holyCommunionDate;

    @Size(max = 150)
    private String holyCommunionPlace;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate confirmationDate;

    @Size(max = 150)
    private String confirmationPlace;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate marriageDate;

    @Size(max = 150)
    private String marriagePlace;
}
