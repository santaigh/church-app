package com.church.app.security;

import com.church.app.entity.ActorType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The authenticated user, whether they came from {@code member} or {@code saas_user}.
 *
 * <p>One class for both, because everything downstream -- authorisation checks, audit
 * entries, tenant filtering -- asks the same questions regardless of which table the
 * account lives in. {@link #actorType} records which one it was.
 *
 * <p>Carries {@code churchId} so tenant filtering needs no extra query on every request.
 * It is null for platform users, which is what "reaches every church" looks like here.
 *
 * <p>Immutable, and holds no JPA entity: this sits in the HTTP session for the length of
 * the visit, long after the persistence context that loaded it has closed.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long userId;
    private final Long churchId;
    private final String churchName;
    private final ActorType actorType;
    private final String username;
    private final String password;
    private final String displayName;
    private final String roleCode;
    private final boolean usingDefaultPassword;
    private final boolean locked;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    private AppUserPrincipal(Builder builder) {
        this.userId = builder.userId;
        this.churchId = builder.churchId;
        this.churchName = builder.churchName;
        this.actorType = builder.actorType;
        this.username = builder.username;
        this.password = builder.password;
        this.displayName = builder.displayName;
        this.roleCode = builder.roleCode;
        this.usingDefaultPassword = builder.usingDefaultPassword;
        this.locked = builder.locked;
        this.active = builder.active;
        this.authorities = List.copyOf(builder.authorities);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() {
        return userId;
    }

    /** Null for platform users, who are not restricted to one church. */
    public Long getChurchId() {
        return churchId;
    }

    /**
     * The parish name, captured at sign-in so the page header costs no query.
     *
     * <p>Null for platform users, for the same reason {@link #getChurchId()} is. Goes
     * stale if the church is renamed mid-session -- corrected at the next sign-in.
     */
    public String getChurchName() {
        return churchName;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    /** True while the account is still on the system default password. */
    public boolean isUsingDefaultPassword() {
        return usingDefaultPassword;
    }

    public boolean isPlatformUser() {
        return actorType == ActorType.SAAS_USER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public static final class Builder {

        private Long userId;
        private Long churchId;
        private String churchName;
        private ActorType actorType = ActorType.MEMBER;
        private String username;
        private String password;
        private String displayName;
        private String roleCode;
        private boolean usingDefaultPassword;
        private boolean locked;
        private boolean active = true;
        private final List<GrantedAuthority> authorities = new ArrayList<>();

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder churchId(Long churchId) {
            this.churchId = churchId;
            return this;
        }

        public Builder churchName(String churchName) {
            this.churchName = churchName;
            return this;
        }

        public Builder actorType(ActorType actorType) {
            this.actorType = actorType;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Adds the role itself as {@code ROLE_<code>}. */
        public Builder role(String roleCode) {
            this.roleCode = roleCode;
            this.authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
            return this;
        }

        /** Adds one {@code PERM_<RESOURCE>_<OPERATION>} authority. */
        public Builder permission(String authority) {
            this.authorities.add(new SimpleGrantedAuthority(authority));
            return this;
        }

        public Builder usingDefaultPassword(boolean usingDefaultPassword) {
            this.usingDefaultPassword = usingDefaultPassword;
            return this;
        }

        public Builder locked(boolean locked) {
            this.locked = locked;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public AppUserPrincipal build() {
            return new AppUserPrincipal(this);
        }
    }
}
