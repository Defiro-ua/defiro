package io.github.defiro.identity.internal;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.FakeOidcProvider;
import io.github.defiro.TestcontainersConfiguration;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// nginx (apps/web/nginx.conf), or Vite, is the only way to the server, and says where the browser is.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class BehindAProxyIT {

    private static final Map<String, String> TLS =
            Map.of("X-Forwarded-Proto", "https", "X-Forwarded-Host", "defiro.cm.test");
    private static final Map<String, String> DEVELOPMENT =
            Map.of("X-Forwarded-Proto", "http", "X-Forwarded-Host", "localhost:3000");

    @DynamicPropertySource
    static void signInThroughTheFakeProvider(DynamicPropertyRegistry registry) {
        FakeOidcProvider.register(registry);
    }

    @LocalServerPort
    int port;

    @Test
    void behindTlsTheSignInReturnsToHttps() {
        assertThat(redirectUri(startSignIn(TLS))).isEqualTo("https://defiro.cm.test/login/oauth2/code/oidc");
    }

    @Test
    void theProxysPortIsKept() {
        assertThat(redirectUri(startSignIn(DEVELOPMENT))).isEqualTo("http://localhost:3000/login/oauth2/code/oidc");
    }

    @Test
    void behindTlsTheCookiesAreSecure() {
        Response response = startSignIn(TLS);

        Cookie session = response.detailedCookie("JSESSIONID");
        assertThat(session.isSecured()).isTrue();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSameSite()).isEqualTo("Lax");
        Cookie csrf = csrfCookie(TLS);
        assertThat(csrf.isSecured()).isTrue();
        assertThat(csrf.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void withoutTlsTheCookiesStillWork() {
        Response response = startSignIn(DEVELOPMENT);

        Cookie session = response.detailedCookie("JSESSIONID");
        assertThat(session.isSecured()).isFalse();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSameSite()).isEqualTo("Lax");
        Cookie csrf = csrfCookie(DEVELOPMENT);
        assertThat(csrf.isSecured()).isFalse();
        assertThat(csrf.getSameSite()).isEqualTo("Lax");
    }

    private Response startSignIn(Map<String, String> proxy) {
        return get("/oauth2/authorization/oidc", proxy);
    }

    private Cookie csrfCookie(Map<String, String> proxy) {
        return get("/api/me", proxy).detailedCookie("XSRF-TOKEN");
    }

    private Response get(String path, Map<String, String> proxy) {
        return given().port(port).headers(proxy).redirects().follow(false).get(path);
    }

    private static String redirectUri(Response toProvider) {
        return Stream.of(URI.create(toProvider.header("Location")).getRawQuery().split("&"))
                .filter(parameter -> parameter.startsWith("redirect_uri="))
                .map(parameter ->
                        URLDecoder.decode(parameter.substring("redirect_uri=".length()), StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
    }
}
