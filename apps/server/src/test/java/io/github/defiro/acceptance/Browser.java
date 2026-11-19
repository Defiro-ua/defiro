package io.github.defiro.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.FakeEntraId;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.HashMap;
import java.util.Map;

/**
 * One person's browser in a scenario. It keeps defiro's cookies and follows the sign-in redirects by
 * hand, through {@link FakeEntraId}, the way a browser does through Microsoft's sign-in page. Like
 * the web app, it reads the CSRF cookie and sends it back in a header.
 */
public final class Browser {

    private final FakeEntraId entraId = FakeEntraId.instance();
    private final Map<String, String> cookies = new HashMap<>();

    /** The first leg: defiro sends the browser to the provider's authorization endpoint. */
    public Response startSignIn() {
        return remember(request().get("/oauth2/authorization/" + FakeEntraId.REGISTRATION_ID));
    }

    /** The whole round trip: defiro, the provider's sign-in, and back to defiro's callback. */
    public Response signIn(String name) {
        entraId.signInAs(name);
        Response toProvider = startSignIn();
        assertThat(toProvider.statusCode())
                .as("defiro sends the browser to Entra ID")
                .isEqualTo(302);
        Response fromProvider =
                given().urlEncodingEnabled(false).redirects().follow(false).get(toProvider.header("Location"));
        assertThat(fromProvider.statusCode())
                .as("Entra ID sends the browser back")
                .isEqualTo(302);
        return remember(request().get(fromProvider.header("Location")));
    }

    /** As the web app does: it loads the profile, which gives it the CSRF cookie, then signs out. */
    public Response signOut() {
        profile();
        return remember(request().post("/logout"));
    }

    public Response profile() {
        return remember(request().get("/api/me"));
    }

    /**
     * A request with this browser's cookies, and the CSRF token in its header once the browser has
     * one. Locations come already encoded, so they are sent as they are, like a browser does.
     */
    public RequestSpecification request() {
        RequestSpecification request =
                given().urlEncodingEnabled(false).redirects().follow(false).cookies(cookies);
        String csrf = cookies.get("XSRF-TOKEN");
        return csrf == null ? request : request.header("X-XSRF-TOKEN", csrf);
    }

    /** The value of one of this browser's cookies, or null if it has none. */
    public String cookie(String name) {
        return cookies.get(name);
    }

    public Response remember(Response response) {
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
