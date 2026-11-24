package io.github.defiro.designer.internal;

import static io.github.defiro.TestPeople.signedInAs;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;

// UC05: the designer offers the organization's groups, as defiro has them.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GroupsIT {

    @Autowired
    MockMvc mvc;

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
    void whoCanRequestAndWhoCanHandleAStepAreTheGroupsInDefiro() throws Exception {
        people.group("RH · Formação");
        people.group("Águas");
        people.group("Divisão Financeira");

        options()
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.handlers[*].name",
                        contains("Chefias da unidade do requerente", "Águas", "Divisão Financeira", "RH · Formação")))
                .andExpect(jsonPath(
                        "$.requesters[*].name",
                        contains("Todos os trabalhadores", "Águas", "Divisão Financeira", "RH · Formação")))
                .andExpect(jsonPath("$.handlers[1].note").value("Grupo da organização"));
    }

    @Test
    void aGroupAddedLaterIsOfferedAtOnce() throws Exception {
        options().andExpect(jsonPath("$.handlers[*].name", not(hasItem("Arquivo"))));

        people.group("Arquivo");

        options().andExpect(jsonPath("$.handlers[*].name", hasItem("Arquivo")));
    }

    @Test
    void aRenamedGroupIsOfferedWithTheNewName() throws Exception {
        UUID archive = people.group("Arquivo");
        options().andExpect(jsonPath("$.handlers[*].name", contains("Chefias da unidade do requerente", "Arquivo")));

        jdbc.sql("update identity.app_group set name = 'Arquivo Municipal' where id = :id")
                .param("id", archive)
                .update();

        options()
                .andExpect(jsonPath(
                        "$.handlers[*].name", contains("Chefias da unidade do requerente", "Arquivo Municipal")));
    }

    @Test
    void withNoGroupsOnlyDefirosOwnOptionsAreOffered() throws Exception {
        options()
                .andExpect(jsonPath("$.handlers[*].name", contains("Chefias da unidade do requerente")))
                .andExpect(jsonPath("$.requesters[*].name", contains("Todos os trabalhadores")));
    }

    private ResultActions options() throws Exception {
        return mvc.perform(get("/api/designer/groups").with(signedInAs(sofia)));
    }
}
