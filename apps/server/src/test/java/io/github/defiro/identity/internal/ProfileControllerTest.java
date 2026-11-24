package io.github.defiro.identity.internal;

import static io.github.defiro.TestPeople.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfiguration.class, OidcRegistration.class})
class ProfileControllerTest {

    static final UUID ANA = UUID.fromString("0f9d3c52-8a51-4f0e-b7c1-2d5e6a7b8c90");

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
    void theProfileIsTheOneDefiroHas() throws Exception {
        when(people.profile(ANA))
                .thenReturn(Optional.of(new Profile(
                        "Ana Ferreira",
                        "Divisão de Obras Municipais",
                        "Técnica Superior",
                        "Rui Matos",
                        List.of("Chefias"),
                        List.of("new-request", "inbox"))));

        MvcResult result = mvc.perform(get("/api/me").with(signedInAs(ANA))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.name")).isEqualTo("Ana Ferreira");
        assertThat((String) JsonPath.read(body, "$.unit")).isEqualTo("Divisão de Obras Municipais");
        assertThat((String) JsonPath.read(body, "$.jobTitle")).isEqualTo("Técnica Superior");
        assertThat((String) JsonPath.read(body, "$.manager")).isEqualTo("Rui Matos");
        assertThat((List<String>) JsonPath.read(body, "$.groups")).containsExactly("Chefias");
        assertThat((List<String>) JsonPath.read(body, "$.menu")).containsExactly("new-request", "inbox");
    }

    @Test
    void someoneDefiroDoesNotHaveIsNotSignedIn() throws Exception {
        when(people.profile(ANA)).thenReturn(Optional.empty());

        MvcResult result = mvc.perform(get("/api/me").with(signedInAs(ANA))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aBrowserStaysSignedInThroughItsSession() throws Exception {
        // What the sign-in leaves in the session once the identity provider sends the browser back.
        when(people.profile(ANA))
                .thenReturn(Optional.of(new Profile("Ana Ferreira", null, null, null, List.of(), List.of("inbox"))));
        OidcIdToken idToken =
                OidcIdToken.withTokenValue("id-token").subject("fake-idp|ana").build();
        var authorities = AuthorityUtils.createAuthorityList("OIDC_USER");
        var person = new SignedInPerson(ANA, new DefaultOidcUser(authorities, idToken));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new OAuth2AuthenticationToken(person, authorities, "oidc")));

        MvcResult result = mvc.perform(get("/api/me").session(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.name"))
                .isEqualTo("Ana Ferreira");
    }
}
