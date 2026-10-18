package com.travelease.admin.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.travelease.admin.repository.UserAccountRepository;
import com.travelease.admin.security.CredentialsVersionValidator;
import com.travelease.common.security.DevJwtDecoders;
import com.travelease.common.security.internal.InternalServiceSecurity;
import com.travelease.common.security.JwtRoleConverters;
import com.travelease.common.security.JwtSecretPolicy;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Bearer-token resource-server baseline for admin-service (api-contract.md §2, NFR-10).
 * Method-level RBAC is enforced at the Controller layer via {@code @PreAuthorize}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.jwt.secret:}")
    private String jwtSecret;

    @Value("${security.internal.secret:}")
    private String internalSecret;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * Internal-only endpoints (routing/policy/recipient lookups used by other services): accept nothing but a trusted
     * service credential. Evaluated before the user-facing chain, so an end user's JWT is never
     * sufficient here, even with the right roles.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalServiceFilterChain(HttpSecurity http) throws Exception {
        return InternalServiceSecurity.filterChain(http, internalSecret, serviceName, "/internal/**");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // proves possession of the temporary password itself; rate-limited like login
                        .requestMatchers(HttpMethod.POST, "/api/auth/complete-password-change").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(JwtRoleConverters.rolesConverter())));
        return http.build();
    }

    // Dev-only fallback; replaced by issuer-uri/jwk-set-uri properties once an IdP is configured.
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(ObjectProvider<UserAccountRepository> userAccounts) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) DevJwtDecoders.hmacDecoder(jwtSecret);
        // standard expiry checks + reject tokens issued before a password/role/active change (#14)
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), new CredentialsVersionValidator(userAccounts)));
        return decoder;
    }

    /** Issues HS256 tokens using the exact same HMAC secret as the resource-server decoder. */
    @Bean
    @ConditionalOnMissingBean(JwtEncoder.class)
    public JwtEncoder jwtEncoder() {
        JwtSecretPolicy.requireUsableSecret(jwtSecret);
        return new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
