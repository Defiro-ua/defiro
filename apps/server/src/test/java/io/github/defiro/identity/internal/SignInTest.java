package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfiguration.class, OidcRegistration.class})
class SignInTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SignIn signIn;

    @MockitoBean
    People people;

    @BeforeEach
    void everyoneIsActive() {
        when(people.access(any())).thenReturn(new Access(true, Set.of()));
    }

    @Test
    void signingInSendsTheBrowserToTheOrganizationsSignInPage() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorization/oidc")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        URI location = URI.create(result.getResponse().getHeader("Location"));
        assertThat(location.getScheme() + "://" + location.getAuthority() + location.getPath())
                .isEqualTo(OidcRegistration.AUTHORIZATION_URI);
        assertThat(location.getQuery()).contains("client_id=defiro-test").contains("response_type=code");
    }

    @Test
    void theApiAnswersUnauthorizedInsteadOfRedirectingToSignIn() throws Exception {
        // The web app calls the API with fetch, which cannot follow a redirect to the provider's page.
        MvcResult result = mvc.perform(get("/api/me")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
