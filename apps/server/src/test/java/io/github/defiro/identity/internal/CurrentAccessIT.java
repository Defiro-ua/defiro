package io.github.defiro.identity.internal;

import static io.github.defiro.TestPeople.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.defiro.FakeOidcProvider;
import io.github.defiro.TestPeople;
import io.github.defiro.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// The roles that count are the ones in the database at each request, not those of the sign-in. Here
// without the cache, so that a change counts at once.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "defiro.identity.access-cache-ttl=0s")
@AutoConfigureMockMvc
class CurrentAccessIT {

    static final String PROTECTED = "/api/designer/step-types";

    @DynamicPropertySource
    static void signInThroughTheFakeProvider(DynamicPropertyRegistry registry) {
        FakeOidcProvider.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    TestPeople people;
    UUID ana;
    MockHttpSession session;

    @BeforeEach
    void anaIsSignedIn() {
        people = new TestPeople(jdbc);
        people.clear();
        ana = people.register("Ana Ferreira", "ana@cm.test");
        session = sessionOf(ana);
    }

    @Test
    void someoneWithoutTheRoleIsForbidden() throws Exception {
        assertThat(status(PROTECTED)).isEqualTo(403);
    }

    @Test
    void aWorkflowManagerIsAllowed() throws Exception {
        people.grant(ana, "WORKFLOW_MANAGER");

        assertThat(status(PROTECTED)).isEqualTo(200);
    }

    @Test
    void anAdministratorIsNotAWorkflowManager() throws Exception {
        people.grant(ana, "ADMINISTRATOR");

        assertThat(status(PROTECTED)).isEqualTo(403);
    }

    @Test
    void aWithdrawnRoleTakesEffectWithoutSigningInAgain() throws Exception {
        people.grant(ana, "WORKFLOW_MANAGER");
        assertThat(status(PROTECTED)).isEqualTo(200);

        people.revoke(ana, "WORKFLOW_MANAGER");

        assertThat(status(PROTECTED)).isEqualTo(403);
    }

    @Test
    void aGrantedRoleTakesEffectWithoutSigningInAgain() throws Exception {
        assertThat(status(PROTECTED)).isEqualTo(403);

        people.grant(ana, "WORKFLOW_MANAGER");

        assertThat(status(PROTECTED)).isEqualTo(200);
    }

    @Test
    void theSessionKeepsNoRoles() throws Exception {
        people.grant(ana, "WORKFLOW_MANAGER");
        assertThat(status(PROTECTED)).isEqualTo(200);

        SecurityContext kept = (SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(kept.getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("OIDC_USER");
    }

    @Test
    void aDeactivatedPersonIsSignedOut() throws Exception {
        assertThat(status("/api/me")).isEqualTo(200);

        people.deactivate(ana);

        assertThat(status("/api/me")).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void someoneNotInTheDatabaseIsSignedOut() throws Exception {
        assertThat(mvc.perform(get("/api/me").with(signedInAs(UUID.randomUUID())))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
    }

    @Test
    void someoneWhoseNameIsNoIdOfDefirosIsSignedOut() throws Exception {
        assertThat(mvc.perform(get("/api/me").with(oidcLogin().idToken(token -> token.subject("fake-idp|ana"))))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
    }

    @Test
    void aRoleLeftInTheSessionCountsForNothing() throws Exception {
        assertThat(mvc.perform(get(PROTECTED)
                                .with(oidcLogin()
                                        .idToken(token -> token.subject(ana.toString()))
                                        .authorities(new SimpleGrantedAuthority("ROLE_WORKFLOW_MANAGER"))))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
    }

    private int status(String path) throws Exception {
        return mvc.perform(get(path).session(session)).andReturn().getResponse().getStatus();
    }

    /** What a sign-in leaves in the session. */
    private static MockHttpSession sessionOf(UUID person) {
        var authorities = AuthorityUtils.createAuthorityList("OIDC_USER");
        OidcIdToken idToken = new OidcIdToken("id-token", null, null, Map.of("sub", "fake-idp|ana"));
        var principal = new SignedInPerson(person, new DefaultOidcUser(authorities, idToken));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new OAuth2AuthenticationToken(principal, authorities, "oidc")));
        return session;
    }
}
