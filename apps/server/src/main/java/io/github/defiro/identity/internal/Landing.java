package io.github.defiro.identity.internal;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/** Where the browser goes after signing in and out. The routes are the web app's. */
final class Landing {

    // The task inbox (UC27). Until UC27 exists, this route of the web app is its placeholder.
    static final String INBOX = "/caixa-de-tarefas";

    // The home page, with a notice; the web app shows the text: «Sessão terminada», «Não foi possível
    // entrar» and «A sua conta está inativa».
    static final String SIGNED_OUT = "/?aviso=sessao-terminada";
    static final String SIGN_IN_FAILED = "/?aviso=entrada-falhada";
    static final String INACTIVE = "/?aviso=conta-inativa";

    private static final RedirectStrategy REDIRECT = new DefaultRedirectStrategy();

    private Landing() {}

    /** Always the inbox, whatever page was asked for before signing in. */
    static AuthenticationSuccessHandler onInbox() {
        return new SimpleUrlAuthenticationSuccessHandler(INBOX);
    }

    /** Home, whether the sign-in failed, was cancelled, or was of someone inactive in defiro. */
    static AuthenticationFailureHandler onFailure() {
        return (request, response, failure) ->
                REDIRECT.sendRedirect(request, response, isInactive(failure) ? INACTIVE : SIGN_IN_FAILED);
    }

    /**
     * Home, through the provider, so that its session ends too. Straight home if the provider has no
     * end-session endpoint, or if there was no session left.
     */
    static LogoutSuccessHandler onSignedOut(ClientRegistrationRepository clientRegistrations) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        handler.setPostLogoutRedirectUri("{baseUrl}" + SIGNED_OUT);
        handler.setDefaultTargetUrl(SIGNED_OUT);
        return handler;
    }

    private static boolean isInactive(AuthenticationException failure) {
        return failure instanceof OAuth2AuthenticationException oauth2
                && DefiroOidcUserService.INACTIVE.equals(oauth2.getError().getErrorCode());
    }
}
