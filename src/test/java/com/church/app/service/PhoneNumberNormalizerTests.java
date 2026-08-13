package com.church.app.service;

import com.church.app.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PhoneNumberNormalizerTests {

    @Autowired
    private PhoneNumberNormalizer normalizer;

    @Autowired
    private MemberRepository memberRepository;

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("every way a parishioner might type their number reaches one form")
    @CsvSource({
            "9840100001,        +919840100001",   // bare local number
            "09840100001,       +919840100001",   // domestic trunk prefix
            "919840100001,      +919840100001",   // country code, no plus
            "+919840100001,     +919840100001",   // already canonical
            "'98401 00001',     +919840100001",   // spaces
            "98401-00001,       +919840100001",   // dashes
            "'+91 98401-00001', +919840100001",   // the lot
            "(98401)00001,      +919840100001"    // brackets
    })
    void normalisesToOneForm(String typed, String expected) {
        assertEquals(expected, normalizer.normalize(typed));
    }

    @Test
    @DisplayName("an explicit foreign country code is left alone")
    void foreignNumbersPassThrough() {
        // Otherwise a deployment configured for +91 would mangle every overseas number.
        assertEquals("+4915112345678", normalizer.normalize("+49 151 12345678"));
        assertEquals("+14155551234", normalizer.normalize("+1 (415) 555-1234"));
        assertEquals("+442071234567", normalizer.normalize("+44 20 7123 4567"));
    }

    @ParameterizedTest
    @DisplayName("input with no digits yields null rather than a bare plus")
    @ValueSource(strings = {"", "   ", "abc", "+", "---", "()"})
    void junkYieldsNull(String junk) {
        assertNull(normalizer.normalize(junk));
    }

    @Test
    void nullIsHandled() {
        assertNull(normalizer.normalize(null));
    }

    @Test
    @DisplayName("email addresses are told apart from phone numbers")
    void distinguishesEmailFromPhone() {
        assertTrue(normalizer.looksLikePhoneNumber("9840100001"));
        assertTrue(normalizer.looksLikePhoneNumber("+919840100001"));
        assertFalse(normalizer.looksLikePhoneNumber("antony.raj@stmarys-chennai.org"));
        assertFalse(normalizer.looksLikePhoneNumber(""));
        assertFalse(normalizer.looksLikePhoneNumber(null));
    }

    @Test
    @DisplayName("the defect this fixes: a number typed without +91 now finds the account")
    void normalisedLookupFindsTheMember() {
        // Every one of these is how a real parishioner might type the same number.
        for (String typed : new String[]{"9840100001", "09840100001", "919840100001",
                "+919840100001", "98401 00001"}) {
            String normalised = normalizer.normalize(typed);
            var member = memberRepository.findByEmailOrMobile(typed, normalised);
            assertTrue(member.isPresent(), "'" + typed + "' should resolve to Antony Raj");
            assertEquals(1L, member.get().getId());
        }
    }

    @Test
    @DisplayName("stored numbers are already canonical, so the UNIQUE index is meaningful")
    void storedNumbersAreCanonical() {
        memberRepository.findAll().stream()
                .map(m -> m.getMobile())
                .filter(mobile -> mobile != null)
                .forEach(mobile -> assertEquals(mobile, normalizer.normalize(mobile),
                        "stored value '" + mobile + "' is not in canonical form"));
    }
}
