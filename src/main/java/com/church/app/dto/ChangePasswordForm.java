package com.church.app.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * The change-password form.
 *
 * <p>No Bean Validation annotations by choice: there is no password policy yet, and the
 * checks that do apply (current password correct, confirmation matches, not a system
 * default) need the stored hash and so belong in the service.
 */
@Getter
@Setter
public class ChangePasswordForm {

    private String currentPassword;

    private String newPassword;

    private String confirmPassword;
}
