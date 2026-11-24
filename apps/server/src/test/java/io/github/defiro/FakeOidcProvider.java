package io.github.defiro;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Plays the organization's identity provider in tests: an OpenID Connect provider on a local port, so
 * no test calls a real one. It has a directory of {@link Account}s; whoever is chosen with {@link
 * #signInAs} is signed in at its authorization endpoint, with no sign-in screen. Its id tokens say
 * only who the person is: sub, name, email and email_verified.
 */
public final class FakeOidcProvider {

    public static final String CLIENT_ID = "defiro-test";
    public static final String CLIENT_SECRET = "fake-oidc-provider-test-secret";
    public static final String REGISTRATION_ID = "oidc";

    private static final FakeOidcProvider INSTANCE = new FakeOidcProvider();

    private final RSAKey key = TestTokens.newKey();
    private final HttpServer server;
    private final Map<String, Account> directory = new ConcurrentHashMap<>();
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private volatile String signingIn;

    /** Someone's account at the provider. */
    public record Account(String name, String email, boolean emailVerified) {

        /** What the provider calls this account. Never an id of defiro's: it is not even a UUID. */
        public String subject() {
            return "fake-idp|" + Integer.toHexString(email.hashCode());
        }
    }

    private record Grant(Account account, String nonce) {}

    private FakeOidcProvider() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/.well-known/openid-configuration", this::discovery);
        server.createContext("/keys", this::keys);
        server.createContext("/authorize", this::authorize);
        server.createContext("/token", this::token);
        server.createContext("/logout", this::logout);
        server.start();
    }

    /** The one provider every test shares; it starts on first use. */
    public static FakeOidcProvider instance() {
        return INSTANCE;
    }

    /** Points defiro at this provider, as the environment variables do in production. */
    public static void register(DynamicPropertyRegistry registry) {
        String prefix = "spring.security.oauth2.client.";
        registry.add(prefix + "registration." + REGISTRATION_ID + ".client-id", () -> CLIENT_ID);
        registry.add(prefix + "registration." + REGISTRATION_ID + ".client-secret", () -> CLIENT_SECRET);
        registry.add(prefix + "registration." + REGISTRATION_ID + ".scope", () -> "openid,profile,email");
        registry.add(prefix + "provider." + REGISTRATION_ID + ".issuer-uri", INSTANCE::issuer);
    }

    public String issuer() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public String authorizationEndpoint() {
        return issuer() + "/authorize";
    }

    public String endSessionEndpoint() {
        return issuer() + "/logout";
    }

    /** Replaces the whole directory. */
    public void directory(List<Account> accounts) {
        directory.clear();
        accounts.forEach(this::save);
    }

    public void save(Account account) {
        directory.put(account.name(), account);
    }

    public Account account(String name) {
        Account account = directory.get(name);
        if (account == null) {
            throw new IllegalArgumentException("No account in the fake provider for " + name);
        }
        return account;
    }

    /** The person the next authorization request signs in. */
    public void signInAs(String name) {
        account(name);
        signingIn = name;
    }

    private void discovery(HttpExchange exchange) throws IOException {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("issuer", issuer());
        config.put("authorization_endpoint", authorizationEndpoint());
        config.put("token_endpoint", issuer() + "/token");
        config.put("jwks_uri", issuer() + "/keys");
        config.put("end_session_endpoint", endSessionEndpoint());
        config.put("response_types_supported", List.of("code"));
        config.put("subject_types_supported", List.of("public"));
        config.put("id_token_signing_alg_values_supported", List.of("RS256"));
        config.put("scopes_supported", List.of("openid", "profile", "email"));
        config.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic"));
        json(exchange, 200, config);
    }

    private void keys(HttpExchange exchange) throws IOException {
        json(exchange, 200, new JWKSet(key.toPublicJWK()).toJSONObject());
    }

    private void authorize(HttpExchange exchange) throws IOException {
        Map<String, String> query = form(exchange.getRequestURI().getRawQuery());
        if (signingIn == null || !CLIENT_ID.equals(query.get("client_id"))) {
            json(exchange, 400, Map.of("error", "invalid_request"));
            return;
        }
        String code = UUID.randomUUID().toString();
        grants.put(code, new Grant(account(signingIn), query.get("nonce")));
        String location = query.get("redirect_uri") + "?code=" + code + "&state="
                + URLEncoder.encode(query.get("state"), StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> body = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Grant grant = grants.remove(String.valueOf(body.get("code")));
        if (!clientAuthenticated(exchange) || grant == null) {
            json(exchange, 400, Map.of("error", "invalid_grant"));
            return;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", UUID.randomUUID().toString());
        response.put("token_type", "Bearer");
        response.put("expires_in", 300);
        response.put("scope", "openid profile email");
        response.put("id_token", idToken(grant));
        json(exchange, 200, response);
    }

    // RP-initiated logout: it needs to know whose session to end, and where to send the browser.
    private void logout(HttpExchange exchange) throws IOException {
        Map<String, String> query = form(exchange.getRequestURI().getRawQuery());
        if (!query.containsKey("id_token_hint") || !query.containsKey("post_logout_redirect_uri")) {
            json(exchange, 400, Map.of("error", "invalid_request"));
            return;
        }
        exchange.getResponseHeaders().add("Location", query.get("post_logout_redirect_uri"));
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private boolean clientAuthenticated(HttpExchange exchange) {
        String expected = "Basic "
                + Base64.getEncoder()
                        .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        return expected.equals(exchange.getRequestHeaders().getFirst("Authorization"));
    }

    private String idToken(Grant grant) {
        return idToken(grant.account(), Map.of("nonce", grant.nonce()));
    }

    /** An ID token of this account, as this provider signs it, with these protocol claims too. */
    public String idToken(Account account, Map<String, Object> protocol) {
        Map<String, Object> claims = new LinkedHashMap<>(protocol);
        claims.put("name", account.name());
        claims.put("email", account.email());
        claims.put("email_verified", account.emailVerified());
        return TestTokens.idToken(key, issuer(), CLIENT_ID, account.subject(), claims);
    }

    private static Map<String, String> form(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            values.put(
                    URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static void json(HttpExchange exchange, int status, Map<String, ?> body) throws IOException {
        byte[] bytes = JSONObjectUtils.toJSONString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
