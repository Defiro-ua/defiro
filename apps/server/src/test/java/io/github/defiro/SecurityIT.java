package io.github.defiro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.defiro.FakeOidcProvider.Account;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIT {

    @DynamicPropertySource
    static void signInThroughTheFakeProvider(DynamicPropertyRegistry registry) {
        FakeOidcProvider.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void requestWithoutASessionIsUnauthorized() throws Exception {
        mvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void aBearerTokenIsNotAWayIn() throws Exception {
        // Even an ID token the provider signed for defiro: people sign in with the browser only.
        String idToken = FakeOidcProvider.instance()
                .idToken(new Account("Ana Ferreira", "ana.ferreira@cm.test", true), Map.of());

        mvc.perform(get("/api/anything").header(HttpHeaders.AUTHORIZATION, "Bearer " + idToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writeWithoutTheCsrfTokenIsRefused() throws Exception {
        // 403, not 401: the CSRF check runs before authentication. If CSRF protection were
        // turned off, this would be 401.
        mvc.perform(post("/api/anything")).andExpect(status().isForbidden());
    }
}
