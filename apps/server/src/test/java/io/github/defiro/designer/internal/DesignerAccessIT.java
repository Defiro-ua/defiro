package io.github.defiro.designer.internal;

import static io.github.defiro.TestPeople.signedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.defiro.TestPeople;
import io.github.defiro.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

// Every Designer endpoint is for Workflow Managers only: the menu hiding «Criar workflow» and
// «Workflows» (UC01) is not enough, since anyone signed in can call the API.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DesignerAccessIT {

    // Ids that exist nowhere: the role is checked before the draft is looked up.
    static final String DRAFT = "/api/designer/drafts/6f1c2a5e-0b7d-4c4e-9a51-3d2f8e7b9c10";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    TestPeople people;
    UUID sofia;
    UUID ana;

    @BeforeEach
    void sofiaIsAWorkflowManagerAndAnaIsNot() {
        people = new TestPeople(jdbc);
        people.clear();
        sofia = people.register("Sofia Almeida", "sofia.almeida@cm.test");
        people.grant(sofia, "WORKFLOW_MANAGER");
        ana = people.register("Ana Ferreira", "ana.ferreira@cm.test");
    }

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
                "GET    | /api/designer/step-types   | {}",
                "GET    | " + DRAFT + "/specification | {}",
                "POST   | /api/designer/drafts/import | {}",
                "GET    | /api/designer/groups       | {}"
            })
    void someoneWithoutTheWorkflowManagerRoleIsRefused(String method, String path, String body) throws Exception {
        // With the CSRF token: without it, writes would be refused before the role is checked.
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .with(signedInAs(ana))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
        "GET, " + DRAFT,
        "GET, /api/designer/drafts",
        "GET, /api/designer/step-types",
        "GET, /api/designer/groups"
    })
    void aWorkflowManagerGetsPastTheCheck(String method, String path) throws Exception {
        // The draft does not exist (404) and the lists are there (200): neither is 401 or 403.
        mvc.perform(request(HttpMethod.valueOf(method), path).with(signedInAs(sofia)))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isIn(200, 404));
    }
}
