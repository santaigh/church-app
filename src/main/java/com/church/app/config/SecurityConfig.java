package com.church.app.config;

import com.church.app.security.AuditingLogoutHandler;
import com.church.app.security.LoginFailureHandler;
import com.church.app.security.LoginSuccessHandler;
import com.church.app.security.MemberUserDetailsService;
import com.church.app.security.SaasUserDetailsService;
import com.church.app.service.AuditService;
import com.church.app.service.LoginAttemptService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Two independent login systems in one application.
 *
 * <p>Parish accounts live in {@code member}; platform accounts live in {@code saas_user}.
 * Rather than one login form searching both tables and guessing, each has its own filter
 * chain with its own {@code UserDetailsService}:
 *
 * <pre>
 *   /saas/**  -> SaasUserDetailsService   -> saas_user  (no church: reaches every parish)
 *   /**       -> MemberUserDetailsService -> member     (scoped to one church)
 * </pre>
 *
 * <p>The separation is real, not cosmetic: a parishioner posting correct credentials to
 * {@code /saas/login} is refused, because that chain's service never reads the
 * {@code member} table. The distinct URL is for clarity and policy, never concealment --
 * every protected page still carries a server-side authority check.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Paths reachable without authentication on the parish chain. */
    private static final String[] PUBLIC_PATHS = {
            "/login", "/forgot-password", "/hello", "/error", "/css/**", "/js/**",
            "/images/**", "/webjars/**", "/favicon.ico", "/actuator/health"
    };

    /**
     * Strength 12 rather than the default 10 -- roughly four times the work per guess,
     * still only a few hundred milliseconds on a login. Sample data was hashed at 12.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Backs the one-session-per-user rule; needs the publisher below to hear session ends. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // ------------------------------------------------------------ platform chain

    @Bean
    @Order(1)
    public SecurityFilterChain saasFilterChain(HttpSecurity http,
                                               SaasUserDetailsService saasUserDetailsService,
                                               AuditService auditService,
                                               LoginAttemptService loginAttemptService,
                                               AuditingLogoutHandler logoutHandler,
                                               SessionRegistry sessionRegistry) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(saasUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        http
                .securityMatcher("/saas/**")
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/saas/login", "/saas/forgot-password").permitAll()
                        // Only the three platform roles, never a parish role.
                        .anyRequest().hasAnyRole("SaaSSAdmin", "SaaSAdmin", "SaaSUser"))
                .formLogin(form -> form
                        .loginPage("/saas/login")
                        .loginProcessingUrl("/saas/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(new LoginSuccessHandler(
                                auditService, loginAttemptService, "/saas/dashboard", true))
                        .failureHandler(new LoginFailureHandler(
                                auditService, loginAttemptService, "/saas/login", true))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/saas/logout")
                        .logoutSuccessUrl("/saas/login?logout")
                        .addLogoutHandler(logoutHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(ex -> ex.accessDeniedPage("/access-denied"));

        applySharedHardening(http, sessionRegistry, "/saas/login?expired");
        return http.build();
    }

    // -------------------------------------------------------------- parish chain

    @Bean
    @Order(2)
    public SecurityFilterChain memberFilterChain(HttpSecurity http,
                                                 MemberUserDetailsService memberUserDetailsService,
                                                 AuditService auditService,
                                                 LoginAttemptService loginAttemptService,
                                                 AuditingLogoutHandler logoutHandler,
                                                 SessionRegistry sessionRegistry) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(memberUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        http
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Error-trigger endpoints exist only under the dev profile.
                        .requestMatchers("/dev/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(new LoginSuccessHandler(
                                auditService, loginAttemptService, "/dashboard", false))
                        .failureHandler(new LoginFailureHandler(
                                auditService, loginAttemptService, "/login", false))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .addLogoutHandler(logoutHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(ex -> ex.accessDeniedPage("/access-denied"));

        applySharedHardening(http, sessionRegistry, "/login?expired");
        return http.build();
    }

    /**
     * Settings both chains share.
     *
     * <p>CSRF is left at its default (enabled). Thymeleaf adds the token automatically to
     * any form using {@code th:action}, and logout is POST-only as a result -- a plain
     * link to {@code /logout} will not work, which is intended: a GET logout can be
     * triggered by an image tag on another site.
     */
    private void applySharedHardening(HttpSecurity http,
                                      SessionRegistry sessionRegistry,
                                      String expiredUrl) throws Exception {
        http
                .sessionManagement(session -> session
                        // A brand new session id on login, so a session id captured before
                        // authentication cannot be reused afterwards.
                        .sessionFixation(fixation -> fixation.newSession())
                        // One session per user: signing in elsewhere ends the older session.
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl(expiredUrl))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {
                        })
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        // Everything is served from this origin; no CDN, no inline script.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; "
                                        + "style-src 'self'; script-src 'self'; "
                                        + "form-action 'self'; frame-ancestors 'none'; base-uri 'self'")));
    }
}
