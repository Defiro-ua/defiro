package io.github.defiro.designer.internal;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.defiro.TestTokens;
import io.github.defiro.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

// UC05: the designer offers the organization's groups and units, as the directory has them.
@Import({TestcontainersConfiguration.class, TestTokens.TrustedIssuer.class})
@SpringBootTest(
        properties = {
            "defiro.directory.groups[0].name=Divisão Financeira",
            "defiro.directory.groups[0].kind=unit",
            "defiro.directory.groups[1].name=Chefias",
            "defiro.directory.groups[1].kind=group"
        })
@AutoConfigureMockMvc
class GroupsIT {

    static final String SOFIA =
            bearer(TRUSTED_KEY, claims -> claims.subject("sofia.almeida").claim("roles", List.of("WorkflowManager")));

    @Autowired
    MockMvc mvc;

    @Test
    void whoCanRequestAndWhoCanHandleAStepComeFromTheDirectory() throws Exception {
        mvc.perform(get("/api/designer/groups").header(HttpHeaders.AUTHORIZATION, SOFIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.requesters[*].name", contains("Todos os trabalhadores", "Divisão Financeira", "Chefias")))
                .andExpect(jsonPath(
                        "$.handlers[*].name",
                        contains("Chefias da unidade do requerente", "Divisão Financeira", "Chefias")))
                .andExpect(jsonPath("$.handlers[1].note").value("Unidade do IdP"))
                .andExpect(jsonPath("$.handlers[2].note").value("Grupo do IdP"));
    }
}
