package io.github.defiro.identity.internal;

import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Who is signed in. Its name is their id in defiro, which is all that the other modules know them by.
 * It keeps no roles: they are read from the database at each request.
 */
final class SignedInPerson extends DefaultOidcUser {

    private static final long serialVersionUID = 1L;

    private final UUID id;

    SignedInPerson(UUID id, OidcUser user) {
        super(fromTheSignIn(user), user.getIdToken(), user.getUserInfo());
        this.id = id;
    }

    @Override
    public String getName() {
        return id.toString();
    }

    private static Collection<? extends GrantedAuthority> fromTheSignIn(OidcUser user) {
        return user.getAuthorities().stream()
                .filter(authority -> "OIDC_USER".equals(authority.getAuthority())
                        || authority.getAuthority().startsWith("SCOPE_"))
                .toList();
    }
}
