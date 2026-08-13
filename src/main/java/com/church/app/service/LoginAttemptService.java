package com.church.app.service;

import com.church.app.config.AppSecurityProperties;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.Member;
import com.church.app.entity.SaasUser;
import com.church.app.repository.MemberRepository;
import com.church.app.repository.SaasUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Tracks sign-in attempts and locks accounts that exceed the failure threshold.
 *
 * <p>Every method runs in its own transaction ({@code REQUIRES_NEW}). This is essential
 * rather than cosmetic: these are called from Spring Security's success and failure
 * handlers, which run <em>after</em> the authentication attempt has already completed and
 * rolled back. Joining that transaction would roll the counter increment back with it,
 * and the lockout could never trigger.
 *
 * <p>There is no automatic unlock. A locked account stays locked until an administrator
 * clears {@code locked_at}.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final MemberRepository memberRepository;
    private final SaasUserRepository saasUserRepository;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final AuditService auditService;
    private final AppSecurityProperties securityProperties;

    public LoginAttemptService(MemberRepository memberRepository,
                               SaasUserRepository saasUserRepository,
                               PhoneNumberNormalizer phoneNumberNormalizer,
                               AuditService auditService,
                               AppSecurityProperties securityProperties) {
        this.memberRepository = memberRepository;
        this.saasUserRepository = saasUserRepository;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
    }

    /**
     * Counts one wrong-password attempt and locks the account if it has now had too many.
     *
     * <p>Only called for a genuinely wrong password. An unknown identifier has no account
     * to count against, and an already-locked account is not counted again -- otherwise
     * the number climbs indefinitely while someone hammers a door that is already shut.
     *
     * @return true if this attempt is the one that locked the account
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerFailure(String identifier, boolean platformScope) {
        int threshold = securityProperties.getMaxFailedLoginAttempts();
        LocalDateTime now = LocalDateTime.now();

        if (platformScope) {
            Optional<SaasUser> found = resolveSaasUser(identifier);
            if (found.isEmpty()) {
                return false;
            }
            SaasUser user = found.get();
            if (user.isLocked()) {
                log.warn("Sign-in attempt on already-locked platform account id={}", user.getId());
                return false;
            }

            saasUserRepository.incrementFailedAttempts(user.getId());
            boolean justLocked = saasUserRepository.lockIfThresholdReached(user.getId(), threshold, now) == 1;
            if (justLocked) {
                log.warn("Platform account '{}' (id={}) LOCKED after {} failed attempts; "
                        + "an administrator must clear locked_at", user.getDisplayName(), user.getId(), threshold);
                auditService.event(AuditEventType.ACCOUNT_LOCKED)
                        .actorSaasUser(user.getId(), user.getDisplayName())
                        .on("saas_user", user.getId(), user.getDisplayName())
                        .describe("Locked after %d consecutive failed sign-in attempts".formatted(threshold))
                        .failed()
                        .save();
            }
            return justLocked;
        }

        Optional<Member> found = resolveMember(identifier);
        if (found.isEmpty()) {
            return false;
        }
        Member member = found.get();
        if (member.isLocked()) {
            log.warn("Sign-in attempt on already-locked member id={}", member.getId());
            return false;
        }

        memberRepository.incrementFailedAttempts(member.getId());
        boolean justLocked = memberRepository.lockIfThresholdReached(member.getId(), threshold, now) == 1;
        if (justLocked) {
            log.warn("Member '{}' (id={}) LOCKED after {} failed attempts; "
                    + "an administrator must clear locked_at", member.getDisplayName(), member.getId(), threshold);
            auditService.event(AuditEventType.ACCOUNT_LOCKED)
                    .actorMember(member.getId(), member.getDisplayName(), member.getChurch().getId())
                    .on("member", member.getId(), member.getDisplayName())
                    .describe("Locked after %d consecutive failed sign-in attempts".formatted(threshold))
                    .failed()
                    .save();
        }
        return justLocked;
    }

    /** Clears the failure counter and stamps the sign-in time. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerSuccess(String identifier, boolean platformScope) {
        LocalDateTime now = LocalDateTime.now();
        if (platformScope) {
            resolveSaasUser(identifier).ifPresent(user ->
                    saasUserRepository.recordSuccessfulLogin(user.getId(), now));
        } else {
            resolveMember(identifier).ifPresent(member ->
                    memberRepository.recordSuccessfulLogin(member.getId(), now));
        }
    }

    private Optional<Member> resolveMember(String identifier) {
        return memberRepository.findByEmailOrMobile(identifier, normalisedMobile(identifier));
    }

    private Optional<SaasUser> resolveSaasUser(String identifier) {
        return saasUserRepository.findByEmailOrMobile(identifier, normalisedMobile(identifier));
    }

    /** Same normalisation as sign-in, so a number typed any way counts against one account. */
    private String normalisedMobile(String identifier) {
        return phoneNumberNormalizer.looksLikePhoneNumber(identifier)
                ? phoneNumberNormalizer.normalize(identifier)
                : null;
    }
}
