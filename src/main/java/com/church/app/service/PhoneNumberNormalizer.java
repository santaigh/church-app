package com.church.app.service;

import com.church.app.config.AppSecurityProperties;
import org.springframework.stereotype.Component;

/**
 * Reduces a typed mobile number to one canonical form, so the same phone always produces
 * the same string.
 *
 * <p>This matters in two places, and the second is easy to overlook:
 *
 * <ol>
 *   <li><b>Login.</b> A parishioner typing {@code 9840100001} must reach the account
 *       stored as {@code +919840100001}.</li>
 *   <li><b>Uniqueness.</b> The UNIQUE index on {@code member.mobile} compares strings.
 *       Without a canonical form, {@code +919840100001} and {@code 9840100001} are two
 *       different values, so one phone could be registered against two members and the
 *       constraint would never notice.</li>
 * </ol>
 *
 * <p>Applied on save as well as on lookup -- normalising only one side would leave the
 * two out of step.
 *
 * <p>Numbers are stored in full international form. A value already carrying an explicit
 * {@code +} country code passes through untouched, so an overseas parishioner is never
 * locked out regardless of the configured default.
 *
 * <p>Deliberately plain string handling rather than a phone-number library: these are
 * overwhelmingly Indian mobiles, and the dependency would not earn its weight.
 */
@Component
public class PhoneNumberNormalizer {

    /** Length of a local mobile number without any country code. */
    private static final int LOCAL_MOBILE_DIGITS = 10;

    private final AppSecurityProperties securityProperties;

    public PhoneNumberNormalizer(AppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * @param rawNumber whatever the user typed
     * @return the number as {@code +<country><digits>}, or null if it held no digits
     */
    public String normalize(String rawNumber) {
        if (rawNumber == null) {
            return null;
        }

        String trimmed = rawNumber.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        boolean explicitCountryCode = trimmed.startsWith("+");

        // Discard anything a human might type as decoration: spaces, dashes, dots,
        // brackets. What remains is the number itself.
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }

        if (explicitCountryCode) {
            // Already qualified -- trust it rather than guessing at a country.
            return "+" + digits;
        }

        // A leading zero is trunk notation for domestic dialling, not part of the
        // number: 09840100001 -> 9840100001
        digits = digits.replaceFirst("^0+", "");
        if (digits.isEmpty()) {
            return null;
        }

        String countryCode = defaultCountryCodeDigits();

        // Longer than a local mobile and already starting with the country code means
        // it was typed without the '+'. Prefixing again would give +91919840100001.
        if (digits.length() > LOCAL_MOBILE_DIGITS && digits.startsWith(countryCode)) {
            return "+" + digits;
        }

        return "+" + countryCode + digits;
    }

    /**
     * True when the value looks like a phone number rather than an email address.
     *
     * <p>Lets one login field accept either: an email address is the only one of the two
     * that can contain an {@code @}.
     */
    public boolean looksLikePhoneNumber(String identifier) {
        return identifier != null && !identifier.isBlank() && !identifier.contains("@");
    }

    private String defaultCountryCodeDigits() {
        return securityProperties.getDefaultCountryCode().replaceAll("\\D", "");
    }
}
