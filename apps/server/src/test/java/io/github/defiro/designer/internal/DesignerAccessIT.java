package io.github.defiro.designer.internal;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.defiro.TestTokens;
import io.github.defiro.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Every Designer endpoint is for Workflow Managers only: the menu hiding «Criar workflow» and
// «Workflows» (UC01) is not enough, since anyone signed in can call the API.
@Import({TestcontainersConfiguration.class, TestTokens.TrustedIssuer.class})
@SpringBootTest
@AutoConfigureMockMvc
class DesignerAccessIT {

    // Ids that exist nowhere: the role is checked before the draft is looked up.
    static final String DRAFT = "/api/designer/drafts/6f1c2a5e-0b7d-4c4e-9a51-3d2f8e7b9c10";

    @Autowired
    MockMvc mvc;

    // The role is checked once the request body is read: each body here is one the endpoint can
    // read, so that the answer is about the role and not about the body.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "POST   | /api/designer/drafts       | {}",
                "GET    | /api/designer/drafts       | {}",
                "GET    | " + DRAFT + "              | {}",
                "PUT    | " + DRAFT + "              | {}",
                "POST   | " + DRAFT + "/publish      | {}",
                "GET    | /api/designer/step-types   | {}"
            })
    void someoneWithoutTheWorkflowManagerRoleIsRefused(String method, String path, String body) throws Exception {
        String employee = bearer(TRUSTED_KEY, claims -> claims.claim("roles", List.of()));

        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .header(HttpHeaders.AUTHORIZATION, employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"GET, " + DRAFT, "GET, /api/designer/drafts", "GET, /api/designer/step-types"})
    void aWorkflowManagerGetsPastTheCheck(String method, String path) throws Exception {
        String workflowManager = bearer(TRUSTED_KEY, claims -> claims.claim("roles", List.of("WorkflowManager")));

        // The draft does not exist (404) and the lists are there (200): neither is 401 or 403.
        mvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION, workflowManager))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isIn(200, 404));
    }
}
