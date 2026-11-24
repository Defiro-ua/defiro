package io.github.defiro;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// The development set-up, for real: the realm and the Keycloak version of deploy/compose.yaml, and the
// dev profile, with its people. The browser is on localhost:3000, where nginx passes it on to the
// server; here that is done by hand, on the server's port.
@Testcontainers
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class KeycloakIT {

    private static final String WEB = "http://localhost:3000";
    private static final String REALM_ID_OF_SOFIA = "5f0a1c9e-6a53-4c0e-9d0b-3a7f1e2d4c01";
    private static final Pattern SIGN_IN_FORM =
            Pattern.compile("<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"");

    @Container
    static final KeycloakContainer KEYCLOAK =
            new KeycloakContainer("quay.io/keycloak/keycloak:26.8.1").withRealmImportFile("/defiro-realm.json");

    @DynamicPropertySource
    static void signInThroughKeycloak(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.oidc.issuer-uri", KeycloakIT::issuer);
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    final Map<String, String> defiroCookies = new HashMap<>();
    final Map<String, String> keycloakCookies = new HashMap<>();

    @BeforeEach
    void nobodyHasSignedInYet() {
        jdbc.sql("delete from identity.external_identity").update();
    }

    @Test
    void sofiaSignsInAndIsThePreRegisteredPerson() {
        Response landed = signIn("defiro");

        assertThat(landed.statusCode()).isEqualTo(302);
        assertThat(URI.create(landed.header("Location")).getPath()).isEqualTo("/caixa-de-tarefas");
        Response me = defiro().get("/api/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.jsonPath().getString("name")).isEqualTo("Sofia Almeida");
        assertThat(me.jsonPath().getList("menu", String.class)).contains("workflows");
        assertThat(me.jsonPath().getList("groups", String.class)).containsExactly("Divisão Financeira");
    }

    @Test
    void theAccountIsLinkedByIssuerAndSubject() {
        signIn("defiro");

        List<Map<String, Object>> accounts = jdbc.sql("""
                        select e.issuer, e.subject, cast(e.user_id as text) as user_id, u.email
                        from identity.external_identity e
                        join identity.app_user u on u.id = e.user_id""").query().listOfRows();
        assertThat(accounts).hasSize(1);
        assertThat(accounts.getFirst())
                .containsEntry("issuer", issuer())
                .containsEntry("subject", REALM_ID_OF_SOFIA)
                .containsEntry("email", "sofia.almeida@defiro.test");
        assertThat(accounts.getFirst().get("user_id"))
                .isEqualTo(jdbc.sql("select cast(id as text) from identity.app_user"
                                + " where department = 'Modernização Administrativa'")
                        .query(String.class)
                        .single())
                .isNotEqualTo(REALM_ID_OF_SOFIA);
    }

    @Test
    void signingOutAlsoEndsTheKeycloakSession() {
        signIn("defiro");
        rememberDefiro(defiro().get("/api/me"));

        Response toKeycloak = rememberDefiro(
                defiro().formParam("_csrf", defiroCookies.get("XSRF-TOKEN")).post("/logout"));
        assertThat(toKeycloak.statusCode()).isEqualTo(302);
        assertThat(toKeycloak.header("Location")).startsWith(issuer());
        Response home = keycloak(toKeycloak.header("Location"));
        assertThat(home.statusCode()).isEqualTo(302);
        assertThat(home.header("Location")).isEqualTo(WEB + "/?aviso=sessao-terminada");

        assertThat(defiro().get("/api/me").statusCode()).isEqualTo(401);
        assertThat(keycloak(startSignIn().header("Location")).statusCode())
                .as("Keycloak asks who is signing in again")
                .isEqualTo(200);
    }

    @Test
    void whileTheKeycloakSessionLastsItDoesNotAskAgain() {
        // What makes the test above say something: without signing out, Keycloak sends the browser back.
        signIn("defiro");
        defiroCookies.clear();

        assertThat(keycloak(startSignIn().header("Location")).statusCode()).isEqualTo(302);
    }

    @Test
    void aWrongPasswordDoesNotSignIn() {
        Response form = signInForm("wrong");

        assertThat(form.statusCode()).isEqualTo(200);
        assertThat(form.header("Location")).isNull();
        assertThat(defiro().get("/api/me").statusCode()).isEqualTo(401);
    }

    @Test
    void theRealmRefusesSignInWithoutPkce() {
        Response response = keycloak(issuer() + "/protocol/openid-connect/auth?client_id=defiro&response_type=code"
                + "&scope=openid&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Flogin%2Foauth2%2Fcode%2Foidc");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.header("Location"))
                .startsWith(WEB + "/login/oauth2/code/oidc?")
                .contains("error=invalid_request")
                .doesNotContain("code=");
    }

    private static String issuer() {
        return KEYCLOAK.getAuthServerUrl().replaceAll("/$", "") + "/realms/defiro";
    }

    /** The whole round trip, up to where defiro sends the browser. */
    private Response signIn(String password) {
        Response back = signInForm(password);
        assertThat(back.statusCode()).as("Keycloak sends the browser back").isEqualTo(302);
        assertThat(back.header("Location")).startsWith(WEB + "/login/oauth2/code/oidc?");
        return rememberDefiro(defiro().get(back.header("Location").substring(WEB.length())));
    }

    /** Keycloak's answer to its sign-in form. */
    private Response signInForm(String password) {
        Response form = keycloak(startSignIn().header("Location"));
        assertThat(form.statusCode()).isEqualTo(200);
        Matcher action = SIGN_IN_FORM.matcher(form.asString());
        assertThat(action.find()).as("Keycloak's sign-in form").isTrue();
        return rememberKeycloak(given().urlEncodingEnabled(false)
                .redirects()
                .follow(false)
                .cookies(keycloakCookies)
                .formParam("username", "sofia.almeida")
                .formParam("password", password)
                .post(URI.create(action.group(1).replace("&amp;", "&"))));
    }

    private Response startSignIn() {
        Response toKeycloak = rememberDefiro(defiro().get("/oauth2/authorization/oidc"));
        assertThat(toKeycloak.statusCode()).isEqualTo(302);
        assertThat(toKeycloak.header("Location")).startsWith(issuer());
        return toKeycloak;
    }

    private Response keycloak(String address) {
        return rememberKeycloak(given().urlEncodingEnabled(false)
                .redirects()
                .follow(false)
                .cookies(keycloakCookies)
                .get(URI.create(address)));
    }

    /** A request as nginx passes it on. */
    private RequestSpecification defiro() {
        return given().port(port)
                .urlEncodingEnabled(false)
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Host", "localhost:3000")
                .redirects()
                .follow(false)
                .cookies(defiroCookies);
    }

    private Response rememberDefiro(Response response) {
        return remember(response, defiroCookies);
    }

    private Response rememberKeycloak(Response response) {
        return remember(response, keycloakCookies);
    }

    // By hand, whatever the cookie's Secure says: a browser trusts localhost, this client does not.
    private static Response remember(Response response, Map<String, String> cookies) {
        for (Cookie cookie : response.detailedCookies()) {
            if (cookie.getMaxAge() == 0 || cookie.getValue().isEmpty()) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }
        return response;
    }
}
