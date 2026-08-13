package com.church.app.security;

import com.church.app.entity.ActorType;
import com.church.app.entity.RoleLevel;
import com.church.app.entity.SaasUser;
import com.church.app.repository.RolePermissionRepository;
import com.church.app.repository.SaasUserRepository;
import com.church.app.service.PhoneNumberNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates platform accounts from the {@code saas_user} table.
 *
 * <p>Serves the {@code /saas/login} chain only. A parish member presented here is
 * refused: this service never reads the {@code member} table.
 *
 * <p>The principal it builds has a null {@code churchId}, which is what allows these
 * accounts to reach every parish.
 */
@Service
public class SaasUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(SaasUserDetailsService.class);

    private final SaasUserRepository saasUserRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PhoneNumberNormalizer phoneNumberNormalizer;

    public SaasUserDetailsService(SaasUserRepository saasUserRepository,
                                  RolePermissionRepository rolePermissionRepository,
                                  PhoneNumberNormalizer phoneNumberNormalizer) {
        this.saasUserRepository = saasUserRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String mobile = phoneNumberNormalizer.looksLikePhoneNumber(identifier)
                ? phoneNumberNormalizer.normalize(identifier)
                : null;

        SaasUser user = saasUserRepository.findByEmailOrMobile(identifier, mobile)
                .orElseThrow(() -> {
                    log.warn("Platform login attempt with unknown identifier '{}'", identifier);
                    return new UsernameNotFoundException("Invalid credentials");
                });

        if (user.getRole().getRoleLevel() != RoleLevel.SAAS) {
            log.warn("saas_user {} holds a non-platform role '{}'", user.getId(), user.getRole().getRoleCode());
            throw new UsernameNotFoundException("Invalid credentials");
        }

        AppUserPrincipal.Builder principal = AppUserPrincipal.builder()
                .userId(user.getId())
                // No churchId, deliberately: platform accounts are not tenant-scoped.
                .actorType(ActorType.SAAS_USER)
                .username(identifier)
                .password(user.getUserPassword())
                .displayName(user.getDisplayName())
                .role(user.getRole().getRoleCode())
                .usingDefaultPassword(user.isUsingDefaultPassword())
                .locked(user.isLocked())
                .active(!user.isDeletedFlag() && "ACTIVE".equals(user.getRecordStatus()));

        rolePermissionRepository.findByRoleId(user.getRole().getId())
                .forEach(permission -> principal.permission(permission.toAuthority()));

        return principal.build();
    }
}
