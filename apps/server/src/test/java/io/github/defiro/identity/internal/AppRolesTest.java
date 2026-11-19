package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class AppRolesTest {

    @Test
    void aBearerTokenWithTheAppRoleMakesAWorkflowManager() {
        Jwt token = token(Map.of("roles", List.of("WorkflowManager"), "scp", "access"));

        assertThat(names(new AppRoles()
                        .jwtAuthenticationConverter()
                        .convert(token)
                        .getAuthorities()))
                .contains("ROLE_WorkflowManager", "SCOPE_access");
    }

    @Test
    void aBearerTokenWithoutRolesGivesNoRole() {
        Jwt token = token(Map.of("name", "Ana Ferreira"));

        assertThat(names(new AppRoles()
                        .jwtAuthenticationConverter()
                        .convert(token)
                        .getAuthorities()))
                .noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    @Test
    void aBrowserSignInWithTheAppRoleMakesAWorkflowManager() {
        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("sub", "sofia.almeida", "roles", List.of("WorkflowManager")));
        OidcUserAuthority signedIn = new OidcUserAuthority(idToken);

        assertThat(names(new AppRoles().userAuthoritiesMapper().mapAuthorities(List.of(signedIn))))
                .contains("ROLE_WorkflowManager", "OIDC_USER");
    }

    @Test
    void aBrowserSignInWithoutRolesGivesNoRole() {
        OidcIdToken idToken = new OidcIdToken(
                "id-token", Instant.now(), Instant.now().plusSeconds(300), Map.of("sub", "ana.ferreira"));

        assertThat(names(
                        new AppRoles().userAuthoritiesMapper().mapAuthorities(List.of(new OidcUserAuthority(idToken)))))
                .containsExactly("OIDC_USER");
    }

    private static Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("someone")
                .claims(all -> all.putAll(claims))
                .build();
    }

    private static Set<String> names(java.util.Collection<? extends GrantedAuthority> authorities) {
        return AuthorityUtils.authorityListToSet(authorities);
    }
}
