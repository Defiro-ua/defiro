package io.github.defiro.identity.internal;

import static io.github.defiro.TestPeople.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfiguration.class, OidcRegistration.class})
class SignOutTest {

    static final UUID ANA = UUID.fromString("0f9d3c52-8a51-4f0e-b7c1-2d5e6a7b8c90");

    static final String TOKEN = "4bb1e2c0-6c35-4b5e-9f0d-3d1f2b7a9e11";

    @Autowired
    MockMvc mvc;

    @Autowired
    ClientRegistrationRepository clientRegistrations;

    @MockitoBean
    SignIn signIn;

    @MockitoBean
    People people;

    @BeforeEach
    void everyoneIsActive() {
        when(people.access(any())).thenReturn(new Access(true, Set.of()));
    }

    @Test
    void signingOutGoesThroughTheProvider() throws Exception {
        MvcResult result = mvc.perform(withTheToken(post("/logout")).with(signedInThroughTheProvider()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(OidcRegistration.END_SESSION + "?");
        assertThat(URI.create(location).getRawQuery().split("&"))
                .extracting(parameter -> URLDecoder.decode(parameter, StandardCharsets.UTF_8))
                .contains("post_logout_redirect_uri=http://localhost/?aviso=sessao-terminada")
                .anyMatch(parameter -> parameter.matches("id_token_hint=.+"));
    }

    @Test
    void aProviderWithoutEndSessionOnlySignsOutOfDefiro() throws Exception {
        ClientRegistrationRepository withoutEndSession = OidcRegistration.with(Map.of());
        MockHttpServletResponse response = new MockHttpServletResponse();
        OidcIdToken idToken =
                OidcIdToken.withTokenValue("id-token").subject("fake-idp|ana").build();
        var authorities = AuthorityUtils.createAuthorityList("OIDC_USER");
        var signedIn = new OAuth2AuthenticationToken(new DefaultOidcUser(authorities, idToken), authorities, "oidc");

        Landing.onSignedOut(withoutEndSession).onLogoutSuccess(new MockHttpServletRequest(), response, signedIn);

        assertThat(response.getRedirectedUrl()).isEqualTo("/?aviso=sessao-terminada");
    }

    @Test
    void signingOutWithAnExpiredSessionGoesHome() throws Exception {
        MvcResult result = mvc.perform(withTheToken(post("/logout"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/?aviso=sessao-terminada");
    }

    @Test
    void theWebAppGetsTheCsrfTokenInACookieItCanRead() throws Exception {
        MvcResult result = mvc.perform(get("/api/me").with(signedInAs(ANA))).andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void aFormWithTheCookieTokenIsAccepted() throws Exception {
        MvcResult result = mvc.perform(post("/logout")
                        .with(signedInAs(ANA))
                        .cookie(new Cookie("XSRF-TOKEN", TOKEN))
                        .param("_csrf", TOKEN))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
    }

    @Test
    void aHeaderWithTheCookieTokenIsStillAccepted() throws Exception {
        // The web app sends it with every write to the Designer.
        MvcResult result = mvc.perform(post("/logout")
                        .with(signedInAs(ANA))
                        .cookie(new Cookie("XSRF-TOKEN", TOKEN))
                        .header("X-XSRF-TOKEN", TOKEN))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
    }

    @Test
    void aFormWithAnotherTokenIsRefused() throws Exception {
        MvcResult result = mvc.perform(post("/logout")
                        .with(signedInAs(ANA))
                        .cookie(new Cookie("XSRF-TOKEN", TOKEN))
                        .param("_csrf", "5cc2f3d1-7d46-4c6f-8a1e-4e2a3c8b0f22"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void signingOutWithoutTheTokenIsRefused() throws Exception {
        // Another site can make the browser post to /logout, but cannot read the cookie.
        MvcResult result = mvc.perform(post("/logout").with(signedInAs(ANA)).cookie(new Cookie("XSRF-TOKEN", TOKEN)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    // As the web app sends it. Not Spring's csrf(), which puts another token repository in the
    // filter, for the other tests too.
    private static MockHttpServletRequestBuilder withTheToken(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", TOKEN)).param("_csrf", TOKEN);
    }

    private RequestPostProcessor signedInThroughTheProvider() {
        return oidcLogin().clientRegistration(clientRegistrations.findByRegistrationId("oidc"));
    }
}
