package io.github.defiro.designer.internal;

import static io.github.defiro.TestPeople.signedInAs;
import static io.github.defiro.designer.internal.WorkflowDraftTest.complete;
import static io.github.defiro.designer.internal.WorkflowDraftTest.withSteps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.defiro.TestPeople;
import io.github.defiro.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

// «Workflows» shows «Versão N · criado por <name> a <date>»: the name is the one defiro
// had for whoever published, at the moment they published.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DraftPublicationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    Drafts drafts;

    @Autowired
    JdbcClient jdbc;

    TestPeople people;
    UUID sofia;

    @BeforeEach
    void sofiaIsAWorkflowManager() {
        people = new TestPeople(jdbc);
        people.clear();
        sofia = people.register("Sofia Almeida", "sofia.almeida@cm.test");
        people.grant(sofia, "WORKFLOW_MANAGER");
    }

    @Test
    void aPublishedDraftShowsWhoPublishedItAndWhen() throws Exception {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();

        mvc.perform(post("/api/designer/drafts/" + id + "/publish")
                        .with(signedInAs(sofia))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/designer/drafts/" + id).with(signedInAs(sofia)))
                .andExpect(jsonPath("$.publishedVersion").value(1))
                .andExpect(jsonPath("$.publishedBy").value("Sofia Almeida"))
                .andExpect(jsonPath("$.publishedAt").value(notNullValue()));
        assertThat(drafts.find(id).orElseThrow().published().by()).isEqualTo(sofia);
    }

    @Test
    void theNameIsTheOneAtThatMoment() throws Exception {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();
        mvc.perform(post("/api/designer/drafts/" + id + "/publish")
                        .with(signedInAs(sofia))
                        .with(csrf()))
                .andExpect(status().isCreated());

        jdbc.sql("update identity.app_user set name = 'Sofia Almeida Costa' where id = :id")
                .param("id", sofia)
                .update();

        mvc.perform(get("/api/designer/drafts/" + id).with(signedInAs(sofia)))
                .andExpect(jsonPath("$.publishedBy").value("Sofia Almeida"));
    }
}
