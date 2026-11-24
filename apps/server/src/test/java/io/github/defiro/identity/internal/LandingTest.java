package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

class LandingTest {

    @Test
    void signingInLandsOnTheInboxEvenWhenAnotherPageWasAskedFirst() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workflows");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new HttpSessionRequestCache().saveRequest(request, response);

        Landing.onInbox()
                .onAuthenticationSuccess(request, response, new TestingAuthenticationToken("ana", null, "OIDC_USER"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/caixa-de-tarefas");
    }

    @Test
    void aFailedOrCancelledSignInGoesHomeWithANotice() throws Exception {
        assertThat(afterFailing("access_denied")).isEqualTo("/?aviso=entrada-falhada");
    }

    @Test
    void anInactiveAccountIsToldSo() throws Exception {
        assertThat(afterFailing("account_inactive")).isEqualTo("/?aviso=conta-inativa");
    }

    private static String afterFailing(String error) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Landing.onFailure()
                .onAuthenticationFailure(
                        new MockHttpServletRequest(), response, new OAuth2AuthenticationException(error));
        return response.getRedirectedUrl();
    }
}
