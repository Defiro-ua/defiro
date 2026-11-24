package io.github.defiro.identity.internal;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    // Health stays public so the app can be probed. Everything else needs someone signed in, in the
    // browser, through the OpenID Connect provider registered in spring.security.oauth2.client, which
    // keeps them signed in with a session. Any provider works; nothing here is specific to one.
    // What they may do is read from the database at each request.
    // With no provider there is no way to sign in, so everything else is denied.
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            SignIn signIn,
            AccessCache accessCache) {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**")
                .permitAll()
                .anyRequest()
                .authenticated());
        http.addFilterBefore(new CurrentAccessFilter(accessCache), AuthorizationFilter.class);
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
        if (registrations == null) {
            http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        } else {
            signInInTheBrowser(http, registrations, signIn);
        }
        return http.build();
    }

    @Bean
    AccessCache accessCache(People people, @Value("${defiro.identity.access-cache-ttl:60s}") Duration ttl) {
        return new AccessCache(people, Ticker.systemTicker(), ttl);
    }

    // Browsers sign in through the provider's page; the API still answers 401, since the web app
    // calls it with fetch, which cannot follow a redirect to that page.
    // The session cookie goes with every request, so writes need the CSRF token. The web app reads
    // it from the XSRF-TOKEN cookie and sends it back: in the X-XSRF-TOKEN header with fetch, and in
    // the _csrf field of the form that signs out, which the browser follows to the provider.
    private static void signInInTheBrowser(
            HttpSecurity http, ClientRegistrationRepository registrations, SignIn signIn) {
        DefiroOidcUserService people = new DefiroOidcUserService(signIn, new OidcUserService());
        http.oauth2Login(login -> login.userInfoEndpoint(userInfo -> userInfo.oidcUserService(people))
                        .successHandler(Landing.onInbox())
                        .failureHandler(Landing.onFailure()))
                .csrf(csrf ->
                        csrf.csrfTokenRepository(csrfCookie()).csrfTokenRequestHandler(theTokenAsItIsInTheCookie()))
                .logout(logout -> logout.logoutSuccessHandler(Landing.onSignedOut(registrations)))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern("/api/**")));
    }

    private static CookieCsrfTokenRepository csrfCookie() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    // Spring masks the token to protect one written in a response's body; the server writes none.
    // With no attribute name, the token is loaded, and its cookie set, from the first request.
    private static CsrfTokenRequestHandler theTokenAsItIsInTheCookie() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
