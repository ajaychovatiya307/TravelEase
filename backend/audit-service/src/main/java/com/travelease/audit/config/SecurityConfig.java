package com.travelease.audit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.travelease.common.security.DevJwtDecoders;
import com.travelease.common.security.internal.InternalServiceSecurity;
import com.travelease.common.security.JwtRoleConverters;

/**
 * Bearer-token resource-server baseline for audit-service (api-contract.md §2, NFR-10).
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
     * Internal-only endpoints (event ingestion (audit records)): accept nothing but a trusted
     * service credential. Evaluated before the user-facing chain, so an end user's JWT is never
     * sufficient here, even with the right roles.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalServiceFilterChain(HttpSecurity http) throws Exception {
        return InternalServiceSecurity.filterChain(http, internalSecret, serviceName, "/api/events", "/api/events/**");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(JwtRoleConverters.rolesConverter())));
        return http.build();
    }

    // Dev-only fallback; replaced by issuer-uri/jwk-set-uri properties once an IdP is configured.
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        return DevJwtDecoders.hmacDecoder(jwtSecret);
    }
}
