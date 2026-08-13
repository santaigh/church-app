package com.church.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bound from {@code app.security.*}. */
@Component
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class AppSecurityProperties {

    /**
     * Dial code assumed when a mobile number is typed without one.
     *
     * <p>Config rather than a constant so a deployment for another country is a one-line
     * change. A number typed with an explicit {@code +} is always honoured as-is, so
     * overseas numbers work regardless of this setting.
     */
    private String defaultCountryCode = "+91";

    /**
     * Password given to a newly created account. Stored BCrypt-hashed with
     * {@code password_flag = 0}, so the first sign-in forces a change.
     */
    private String defaultPassword = "Welcome123$";

    /**
     * Password an account is returned to by "forgot password". Deliberately different
     * from {@link #defaultPassword} so an account that has been reset is distinguishable
     * from one that was never used.
     */
    private String resetPassword = "PleaseReset@123";

    /**
     * Cap on "forgot password" requests per account per hour.
     *
     * <p>Because a reset returns the account to a known password, an unlimited form would
     * let anyone who knows a parishioner's mobile number repeatedly overwrite their
     * password -- locking the real user out and leaving a credential the attacker knows.
     */
    private int maxPasswordResetsPerHour = 3;

    /**
     * Consecutive wrong-password attempts before an account is locked.
     *
     * <p>There is no automatic expiry by design: a locked account stays locked until an
     * administrator clears {@code locked_at}.
     */
    private int maxFailedLoginAttempts = 5;
}
