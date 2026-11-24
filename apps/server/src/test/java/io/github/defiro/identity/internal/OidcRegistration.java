package io.github.defiro.identity.internal;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * For slice tests: defiro registered with an identity provider, as the environment variables do in
 * production. Nothing calls these addresses; they only show up in redirects.
 */
@TestConfiguration(proxyBeanMethods = false)
class OidcRegistration {

    static final String AUTHORIZATION_URI = "https://idp.example.test/authorize";

    static final String END_SESSION = "https://idp.example.test/logout";

    @Bean
    ClientRegistrationRepository clientRegistrations() {
        return with(Map.of("end_session_endpoint", END_SESSION));
    }

    /** A provider whose discovery document says this. */
    static ClientRegistrationRepository with(Map<String, Object> metadata) {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("oidc")
                .clientId("defiro-test")
                .clientSecret("slice-test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(AUTHORIZATION_URI)
                .tokenUri("https://idp.example.test/token")
                .jwkSetUri("https://idp.example.test/keys")
                .userNameAttributeName("sub")
                .providerConfigurationMetadata(metadata)
                .build());
    }
}
