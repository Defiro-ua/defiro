package io.github.defiro.identity.internal;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Turns the account the identity provider signed in into a person in defiro. From the token it reads
 * only who they are: iss, sub, name, email and email_verified.
 */
class DefiroOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    /** The error code of a sign-in refused because the person is inactive in defiro. */
    static final String INACTIVE = "account_inactive";

    private final SignIn signIn;
    private final OAuth2UserService<OidcUserRequest, OidcUser> provider;

    DefiroOidcUserService(SignIn signIn, OAuth2UserService<OidcUserRequest, OidcUser> provider) {
        this.signIn = signIn;
        this.provider = provider;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = provider.loadUser(request);
        IdpAccount account = new IdpAccount(
                user.getClaimAsString("iss"),
                user.getSubject(),
                user.getFullName(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getEmailVerified()));
        try {
            return new SignedInPerson(resolve(account), user);
        } catch (InactiveAccountException inactive) {
            throw new OAuth2AuthenticationException(new OAuth2Error(INACTIVE), inactive);
        }
    }

    // Two first sign-ins at once: the second finds the rows the first added. The failed transaction
    // can do nothing more, so this is a new one.
    private UUID resolve(IdpAccount account) {
        try {
            return signIn.resolve(account);
        } catch (DuplicateKeyException alreadyThere) {
            return signIn.resolve(account);
        }
    }
}
