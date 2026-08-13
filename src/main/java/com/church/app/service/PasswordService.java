package com.church.app.service;

import com.church.app.config.AppSecurityProperties;
import com.church.app.entity.ActorType;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.Member;
import com.church.app.entity.SaasUser;
import com.church.app.exception.BusinessException;
import com.church.app.exception.ResourceNotFoundException;
import com.church.app.repository.AuditLogRepository;
import com.church.app.repository.MemberRepository;
import com.church.app.repository.SaasUserRepository;
import com.church.app.security.AppUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Password lifecycle for both account tables.
 *
 * <p>{@code password_flag} drives everything: 0 means the account is on a system-assigned
 * password and cannot be used for anything until it is changed; 1 means the user chose it.
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private final MemberRepository memberRepository;
    private final SaasUserRepository saasUserRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final AuditService auditService;
    private final AppSecurityProperties securityProperties;

    public PasswordService(MemberRepository memberRepository,
                           SaasUserRepository saasUserRepository,
                           AuditLogRepository auditLogRepository,
                           PasswordEncoder passwordEncoder,
                           PhoneNumberNormalizer phoneNumberNormalizer,
                           AuditService auditService,
                           AppSecurityProperties securityProperties) {
        this.memberRepository = memberRepository;
        this.saasUserRepository = saasUserRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
    }

    /** BCrypt hash of the password assigned to a new account. */
    public String encodedDefaultPassword() {
        return passwordEncoder.encode(securityProperties.getDefaultPassword());
    }

    // ------------------------------------------------------------ change password

    /**
     * The user replaces their own password, flipping {@code password_flag} to 1.
     *
     * @throws BusinessException if the current password is wrong, the confirmation does
     *                           not match, or the new password is one of the system defaults
     */
    @Transactional
    public void changeOwnPassword(AppUserPrincipal principal,
                                  String currentPassword,
                                  String newPassword,
                                  String confirmPassword) {
        if (principal.getActorType() == ActorType.SAAS_USER) {
            SaasUser user = saasUserRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Platform user", principal.getUserId()));

            verifyAndValidate(currentPassword, newPassword, confirmPassword,
                    user.getUserPassword(), user.getEmail());

            user.setUserPassword(passwordEncoder.encode(newPassword));
            user.setPasswordFlag(true);
            user.setPasswordChangedAt(LocalDateTime.now());
            saasUserRepository.save(user);

            auditService.event(AuditEventType.PASSWORD_CHANGED)
                    .actorSaasUser(user.getId(), user.getDisplayName())
                    .on("saas_user", user.getId(), user.getDisplayName())
                    .describe("Password changed by the user; password_flag 0 -> 1")
                    .save();
        } else {
            Member member = memberRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Member", principal.getUserId()));

            verifyAndValidate(currentPassword, newPassword, confirmPassword,
                    member.getMemberPassword(), member.getEmail());

            member.setMemberPassword(passwordEncoder.encode(newPassword));
            member.setPasswordFlag(true);
            member.setPasswordChangedAt(LocalDateTime.now());
            memberRepository.save(member);

            auditService.event(AuditEventType.PASSWORD_CHANGED)
                    .actorMember(member.getId(), member.getDisplayName(), member.getChurch().getId())
                    .on("member", member.getId(), member.getDisplayName())
                    .describe("Password changed by the user; password_flag 0 -> 1")
                    .save();
        }

        log.info("Password changed for {} id={}", principal.getActorType(), principal.getUserId());
    }

    // ------------------------------------------------------------ forgot password

    /**
     * Returns an account to the reset password and {@code password_flag = 0}.
     *
     * <p>Silent about whether the account exists -- the caller shows the same confirmation
     * either way, so the form cannot be used to discover which numbers and addresses
     * belong to real parishioners.
     *
     * <p>Deliberately does NOT unlock a locked account: unlocking is an administrator
     * action, and letting a user clear their own lock by resetting would defeat it.
     *
     * @param platformScope true to search {@code saas_user}, false to search {@code member}
     */
    @Transactional
    public void resetToDefaultPassword(String identifier, boolean platformScope) {
        if (!StringUtils.hasText(identifier)) {
            return;
        }

        String trimmed = identifier.trim();
        String mobile = phoneNumberNormalizer.looksLikePhoneNumber(trimmed)
                ? phoneNumberNormalizer.normalize(trimmed)
                : null;

        if (platformScope) {
            Optional<SaasUser> found = saasUserRepository.findByEmailOrMobile(trimmed, mobile);
            if (found.isEmpty()) {
                log.warn("Password reset requested for unknown platform identifier '{}'", trimmed);
                return;
            }
            SaasUser user = found.get();
            // Platform accounts belong to no church, hence the null.
            if (resetLimitReached(ActorType.SAAS_USER, user.getId(), user.getDisplayName(), null)) {
                return;
            }

            user.setUserPassword(passwordEncoder.encode(securityProperties.getResetPassword()));
            user.setPasswordFlag(false);
            saasUserRepository.save(user);

            auditService.event(AuditEventType.PASSWORD_RESET)
                    .actorSaasUser(user.getId(), user.getDisplayName())
                    .on("saas_user", user.getId(), user.getDisplayName())
                    .describe("Reset to the system password via forgot-password using '" + trimmed + "'")
                    .save();
            log.warn("Platform account '{}' (id={}) reset to the system password",
                    user.getDisplayName(), user.getId());
        } else {
            Optional<Member> found = memberRepository.findByEmailOrMobile(trimmed, mobile);
            if (found.isEmpty()) {
                log.warn("Password reset requested for unknown identifier '{}'", trimmed);
                return;
            }
            Member member = found.get();
            if (resetLimitReached(ActorType.MEMBER, member.getId(), member.getDisplayName(),
                    member.getChurch().getId())) {
                return;
            }

            member.setMemberPassword(passwordEncoder.encode(securityProperties.getResetPassword()));
            member.setPasswordFlag(false);
            memberRepository.save(member);

            auditService.event(AuditEventType.PASSWORD_RESET)
                    .actorMember(member.getId(), member.getDisplayName(), member.getChurch().getId())
                    .on("member", member.getId(), member.getDisplayName())
                    .describe("Reset to the system password via forgot-password using '" + trimmed + "'")
                    .save();
            log.warn("Member '{}' (id={}) reset to the system password",
                    member.getDisplayName(), member.getId());
        }
    }

    /**
     * True when this account has already been reset too many times in the last hour.
     *
     * <p>Counted from the audit trail, which is already recording these events.
     */
    private boolean resetLimitReached(ActorType actorType, Long actorId, String displayName, Long churchId) {
        long recent = auditLogRepository.countByActorTypeAndActorIdAndEventTypeAndEventTimeAfter(
                actorType, actorId, AuditEventType.PASSWORD_RESET, LocalDateTime.now().minusHours(1));

        if (recent < securityProperties.getMaxPasswordResetsPerHour()) {
            return false;
        }

        log.warn("Password reset REFUSED for {} id={} ('{}'): {} resets already in the last hour",
                actorType, actorId, displayName, recent);

        // Attribute the refusal to the targeted account. A row saying only "reset
        // refused" would not tell an investigator whose account was under attack.
        var entry = auditService.event(AuditEventType.PASSWORD_RESET);
        if (actorType == ActorType.SAAS_USER) {
            entry.actorSaasUser(actorId, displayName);
        } else {
            entry.actorMember(actorId, displayName, churchId);
        }
        entry.describe("Reset refused: rate limit of %d per hour reached"
                        .formatted(securityProperties.getMaxPasswordResetsPerHour()))
                .failed()
                .save();
        return true;
    }

    // ------------------------------------------------------------------- validation

    private void verifyAndValidate(String currentPassword, String newPassword, String confirmPassword,
                                   String storedHash, String accountLabel) {
        if (!passwordEncoder.matches(currentPassword, storedHash)) {
            log.warn("Password change rejected for '{}': current password did not match", accountLabel);
            throw new BusinessException("Your current password is not correct.",
                    HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_MISMATCH");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new BusinessException("Enter a new password.",
                    HttpStatus.BAD_REQUEST, "PASSWORD_REQUIRED");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("The two passwords do not match.",
                    HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRM_MISMATCH");
        }
        // No complexity policy by choice -- but the system passwords are barred, or
        // password_flag = 1 would no longer mean the user had chosen anything.
        if (newPassword.equals(securityProperties.getDefaultPassword())
                || newPassword.equals(securityProperties.getResetPassword())) {
            throw new BusinessException("You cannot reuse a system-assigned password. Please choose your own.",
                    HttpStatus.BAD_REQUEST, "PASSWORD_IS_SYSTEM_DEFAULT");
        }
        if (passwordEncoder.matches(newPassword, storedHash)) {
            throw new BusinessException("The new password must be different from your current one.",
                    HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED");
        }
    }
}
