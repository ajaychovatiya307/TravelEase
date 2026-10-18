package com.travelease.common.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Shared factory for the JWT-&gt;authorities conversion used by every service's resource-server
 * config (api-contract.md §2, Constitution §2 cross-cutting rule). Maps the token's "roles"
 * claim to Spring Security authorities prefixed with "ROLE_", matching {@link Roles} values.
 */
public final class JwtRoleConverters {

    private static final String ROLES_CLAIM = "roles";
    private static final String AUTHORITY_PREFIX = "ROLE_";

    private JwtRoleConverters() {
        // factory holder - not instantiable
    }

    public static JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix(AUTHORITY_PREFIX);
        authoritiesConverter.setAuthoritiesClaimName(ROLES_CLAIM);

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
