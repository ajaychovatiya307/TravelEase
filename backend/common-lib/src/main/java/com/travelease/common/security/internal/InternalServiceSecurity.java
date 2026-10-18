package com.travelease.common.security.internal;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Builds the security filter chain for internal-only endpoints. Register it with a higher priority
 * ({@code @Order(1)}) than the service's user-facing chain:
 *
 * <pre>
 * &#64;Bean &#64;Order(1)
 * SecurityFilterChain internalChain(HttpSecurity http) throws Exception {
 *     return InternalServiceSecurity.filterChain(http, internalSecret, appName, "/api/events", "/api/events/**");
 * }
 * </pre>
 *
 * Only a valid {@link InternalServiceTokens} credential in {@value InternalServiceTokens#HEADER} is
 * accepted; bearer user tokens are not consulted at all on these paths (401 without the credential).
 */
public final class InternalServiceSecurity {

    public static final String ROLE = "INTERNAL_SERVICE";

    private InternalServiceSecurity() {
        // utility holder - not instantiable
    }

    public static SecurityFilterChain filterChain(HttpSecurity http, String internalSecret, String serviceName,
            String... paths) throws Exception {
        InternalServiceTokens tokens = new InternalServiceTokens(internalSecret, serviceName);
        http
                .securityMatcher(paths)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalServiceAuthenticationFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(ROLE))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                                        "A trusted internal service credential is required"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN_ACTION",
                                        "Only internal services may call this endpoint")));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + code + "\",\"message\":\""
                + message + "\",\"fieldErrors\":null}");
    }

    /** Authenticates requests that carry a genuine internal-service credential. */
    static final class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

        private final InternalServiceTokens tokens;

        InternalServiceAuthenticationFilter(InternalServiceTokens tokens) {
            this.tokens = tokens;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            tokens.verify(request.getHeader(InternalServiceTokens.HEADER)).ifPresent(service ->
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            "service:" + service, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)))));
            chain.doFilter(request, response);
        }
    }
}
