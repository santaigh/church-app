package com.church.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * What the anbiyam screen submits.
 *
 * <p>Note what is <b>not</b> here: {@code churchId}. The parish comes from the tenant
 * scope of the request, never from the form. A hidden field carrying a church id would be
 * trusted by the server and is exactly how one parish ends up writing into another's
 * records.
 */
@Getter
@Setter
public class AnbiyamForm {

    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name may be at most 150 characters")
    private String anbiyamName;

    @Size(max = 255, message = "Area description may be at most 255 characters")
    private String areaDescription;

    /**
     * The animator who leads this anbiyam -- a member of this parish, or none yet.
     *
     * <p>Validated against the tenant scope on save. The dropdown only offers members of
     * this parish, but a posted id is not evidence of anything, so the service checks.
     */
    private Long headMemberId;

    /** New anbiyams start active; the toggle is how one is retired without deleting it. */
    private boolean activeFlag = true;
}
