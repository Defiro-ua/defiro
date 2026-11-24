package io.github.defiro.designer.internal;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static io.github.defiro.designer.internal.WorkflowDraftTest.complete;
import static io.github.defiro.designer.internal.WorkflowDraftTest.withSteps;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.defiro.TestTokens;
import io.github.defiro.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// A workflow travels as a file: the published version goes out, and a file comes in as a new draft.
@Import({TestcontainersConfiguration.class, TestTokens.TrustedIssuer.class})
@SpringBootTest
@AutoConfigureMockMvc
class DraftFilesIT {

    static final String SOFIA = bearer(
            TRUSTED_KEY,
            claims -> claims.subject("sofia.almeida")
                    .claim("name", "Sofia Almeida")
                    .claim("roles", List.of("WorkflowManager")));

    @Autowired
    MockMvc mvc;

    @Autowired
    Drafts drafts;

    @Test
    void theFileOfAWorkflowIsNamedAfterItAndItsVersion() throws Exception {
        UUID id = drafts.add(WorkflowDraftTest.named("Ajudas de custo – Ação Social", complete("Validação da chefia")))
                .id();
        mvc.perform(post("/api/designer/drafts/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, SOFIA))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/designer/drafts/" + id + "/specification").header(HttpHeaders.AUTHORIZATION, SOFIA))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"ajudas-de-custo-acao-social-v1.json\""))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.name").value("Ajudas de custo – Ação Social"))
                .andExpect(jsonPath("$.steps[0].fields[0].label").value("Material pedido"))
                .andExpect(jsonPath("$.steps[0].fields[0].type").value("short-text"))
                .andExpect(jsonPath("$.steps[1].fields").isEmpty())
                .andExpect(jsonPath("$.transitions").isNotEmpty());
    }

    @Test
    void aWorkflowNeverCreatedHasNoFile() throws Exception {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();

        mvc.perform(get("/api/designer/drafts/" + id + "/specification").header(HttpHeaders.AUTHORIZATION, SOFIA))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFileStartsANewDraft() throws Exception {
        String file = """
                {"schemaVersion": 1, "name": "Horas extraordinárias", "transitions": [],
                 "steps": [{"id": "pedido", "plugin": {"name": "submission", "version": "0.1.0"}, "name": "Pedido",
                            "handledBy": "Todos os trabalhadores", "deadline": null,
                            "returnReasons": [], "rejectReasons": [],
                            "fields": [{"id": "q1", "label": "Transporte", "type": "single-choice", "required": true,
                                        "options": ["Carro próprio", "Comboio"]}]}]}""";

        mvc.perform(post("/api/designer/drafts/import")
                        .header(HttpHeaders.AUTHORIZATION, SOFIA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Horas extraordinárias"))
                .andExpect(jsonPath("$.steps[0].name").value("Pedido"))
                .andExpect(jsonPath("$.steps[0].fields[0].label").value("Transporte"))
                .andExpect(jsonPath("$.steps[0].fields[0].options[1]").value("Comboio"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.publishedVersion").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "olá                                                   | O ficheiro não é um workflow da defiro.",
                "{\"title\": \"Lista de compras\"}                     | O ficheiro não é um workflow da defiro.",
                "{\"schemaVersion\": 7, \"name\": \"X\", \"steps\": [{}]} | O ficheiro é de uma versão do formato que esta defiro não conhece (7).",
                "{\"schemaVersion\": 1, \"name\": \"X\", \"steps\": []}  | O ficheiro não tem passos."
            })
    void aFileThatIsNotAWorkflowIsRefused(String file, String message) throws Exception {
        mvc.perform(post("/api/designer/drafts/import")
                        .header(HttpHeaders.AUTHORIZATION, SOFIA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(message));
    }
}
