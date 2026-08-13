package com.church.app.security;

import com.church.app.entity.ActorType;
import com.church.app.entity.Member;
import com.church.app.entity.RoleLevel;
import com.church.app.repository.MemberRepository;
import com.church.app.repository.RolePermissionRepository;
import com.church.app.service.PhoneNumberNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates parish accounts from the {@code member} table.
 *
 * <p>Serves the {@code /login} chain only. A platform account presented here is refused
 * even with the correct password -- the separation between the two login pages is
 * enforced by which table each service reads, not by the URL.
 */
@Service
public class MemberUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(MemberUserDetailsService.class);

    private final MemberRepository memberRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PhoneNumberNormalizer phoneNumberNormalizer;

    public MemberUserDetailsService(MemberRepository memberRepository,
                                    RolePermissionRepository rolePermissionRepository,
                                    PhoneNumberNormalizer phoneNumberNormalizer) {
        this.memberRepository = memberRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
    }

    /**
     * @param identifier the member's email address or mobile number -- both are unique
     *                   where present, so at most one account can match
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // A mobile is normalised to the stored form first, so 9840100001,
        // 098401 00001 and +919840100001 all reach the same account.
        String mobile = phoneNumberNormalizer.looksLikePhoneNumber(identifier)
                ? phoneNumberNormalizer.normalize(identifier)
                : null;

        Member member = memberRepository.findByEmailOrMobile(identifier, mobile)
                .orElseThrow(() -> {
                    // Logged, never shown. The login page reports the same message for
                    // every failure so valid accounts cannot be enumerated.
                    log.warn("Login attempt with unknown identifier '{}'", identifier);
                    return new UsernameNotFoundException("Invalid credentials");
                });

        if (member.getRole().getRoleLevel() != RoleLevel.APP) {
            log.warn("Member {} holds a platform-level role '{}' and cannot sign in here",
                    member.getId(), member.getRole().getRoleCode());
            throw new UsernameNotFoundException("Invalid credentials");
        }

        AppUserPrincipal.Builder principal = AppUserPrincipal.builder()
                .userId(member.getId())
                .churchId(member.getChurch().getId())
                .actorType(ActorType.MEMBER)
                .username(identifier)
                .password(member.getMemberPassword())
                .displayName(member.getDisplayName())
                .role(member.getRole().getRoleCode())
                .usingDefaultPassword(member.isUsingDefaultPassword())
                .locked(member.isLocked())
                .active(!member.isDeletedFlag() && "ACTIVE".equals(member.getRecordStatus()));

        rolePermissionRepository.findByRoleId(member.getRole().getId())
                .forEach(permission -> principal.permission(permission.toAuthority()));

        return principal.build();
    }
}
