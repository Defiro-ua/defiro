package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class DefiroOidcUserServiceTest {

    private static final UUID ID = UUID.fromString("0f9d3c52-8a51-4f0e-b7c1-2d5e6a7b8c90");
    private static final OidcUserRequest REQUEST = mock(OidcUserRequest.class);

    private final SignIn signIn = mock(SignIn.class);

    @Test
    void thePrincipalIsNamedAfterThePersonInDefiro() {
        when(signIn.resolve(any())).thenReturn(ID);
        OidcUser fromProvider = user(Map.of());

        OidcUser person = signedIn(fromProvider);

        assertThat(person.getName()).isEqualTo(ID.toString());
        assertThat(person.getIdToken()).isSameAs(fromProvider.getIdToken());
    }

    @Test
    void onlyTheFiveClaimsReachDefiro() {
        when(signIn.resolve(any())).thenReturn(ID);

        signedIn(user(Map.of(
                "name",
                "Ana Ferreira",
                "email",
                "ana@cm.test",
                "email_verified",
                true,
                "oid",
                "8f1c2a5e-0000-4000-8000-000000000001",
                "roles",
                List.of("ADMINISTRATOR"),
                "groups",
                List.of("Chefias"))));

        verify(signIn).resolve(new IdpAccount("https://idp.test", "idp-sub", "Ana Ferreira", "ana@cm.test", true));
    }

    @Test
    void aMissingEmailVerifiedIsNotVerified() {
        when(signIn.resolve(any())).thenReturn(ID);

        signedIn(user(Map.of("name", "Ana Ferreira", "email", "ana@cm.test")));

        verify(signIn).resolve(new IdpAccount("https://idp.test", "idp-sub", "Ana Ferreira", "ana@cm.test", false));
    }

    @Test
    void rolesInTheTokenGrantNothing() {
        when(signIn.resolve(any())).thenReturn(ID);

        OidcUser person = signedIn(user(Map.of("roles", List.of("ADMINISTRATOR"))));

        assertThat(person.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("OIDC_USER", "SCOPE_openid");
    }

    @Test
    void anInactiveAccountFailsTheSignIn() {
        when(signIn.resolve(any())).thenThrow(new InactiveAccountException());

        assertThatThrownBy(() -> signedIn(user(Map.of())))
                .isInstanceOfSatisfying(
                        OAuth2AuthenticationException.class,
                        failure -> assertThat(failure.getError().getErrorCode()).isEqualTo("account_inactive"));
    }

    @Test
    void losingARaceForTheSameAccountTriesOnceMore() {
        when(signIn.resolve(any()))
                .thenThrow(new DuplicateKeyException("external_identity"))
                .thenReturn(ID);

        assertThat(signedIn(user(Map.of())).getName()).isEqualTo(ID.toString());
        verify(signIn, times(2)).resolve(any());
    }

    @Test
    void losingTheRaceTwiceIsAFailure() {
        when(signIn.resolve(any())).thenThrow(new DuplicateKeyException("external_identity"));

        assertThatThrownBy(() -> signedIn(user(Map.of()))).isInstanceOf(DuplicateKeyException.class);
        verify(signIn, times(2)).resolve(any());
    }

    private OidcUser signedIn(OidcUser fromProvider) {
        return new DefiroOidcUserService(signIn, request -> fromProvider).loadUser(REQUEST);
    }

    private static OidcUser user(Map<String, Object> claims) {
        Map<String, Object> all = new HashMap<>(claims);
        all.put("iss", "https://idp.test");
        all.put("sub", "idp-sub");
        // Whatever the provider's own mapping adds, such as ROLE_ADMINISTRATOR, is not kept.
        return new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("OIDC_USER", "SCOPE_openid", "ROLE_ADMINISTRATOR"),
                new OidcIdToken("id-token", null, null, all));
    }
}
