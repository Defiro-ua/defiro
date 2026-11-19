package io.github.defiro.identity.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * The app roles Entra ID puts in the {@code roles} claim become Spring Security roles, so the
 * server, and not only the menu, decides what each person may do. A person with the app role
 * {@code WorkflowManager} gets {@code ROLE_WorkflowManager}, which {@code hasRole('WorkflowManager')}
 * checks. Both ways of signing in get them: a bearer token, and the id token of a browser sign-in.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class AppRoles {

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(token -> {
            List<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(token));
            authorities.addAll(rolesIn(token));
            return authorities;
        });
        return converter;
    }

    @Bean
    GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return signedIn -> {
            List<GrantedAuthority> authorities = new ArrayList<>(signedIn);
            signedIn.stream()
                    .filter(OidcUserAuthority.class::isInstance)
                    .map(OidcUserAuthority.class::cast)
                    .forEach(user -> authorities.addAll(rolesIn(user.getIdToken())));
            return authorities;
        };
    }

    private static Collection<GrantedAuthority> rolesIn(ClaimAccessor claims) {
        List<String> roles = claims.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
