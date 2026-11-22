package io.github.defiro.engine.internal;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.defiro.TestTokens;
import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.WorkflowSpecification;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

// A file is sent as it was attached, but the browser must never run what is in it on defiro's
// address, with the reviewer's session: only PDFs and images open in the browser.
@Import({TestcontainersConfiguration.class, TestTokens.TrustedIssuer.class})
@SpringBootTest
@AutoConfigureMockMvc
class RequestFilesIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProcedureSeed seed;

    @Autowired
    Deployment deployment;

    final String reference = "IT-" + UUID.randomUUID().toString().substring(0, 8);
    final String ana = bearer(TRUSTED_KEY, claims -> claims.subject("ana"));

    ProcedureSeed.Submitted submitted;

    @BeforeEach
    void anaSubmittedARequest() {
        StepSpecification request = step("Pedido", "submission");
        StepSpecification review = step("Verificação financeira", "review");
        UUID draft = UUID.randomUUID();
        int version = deployment.deploy(
                new WorkflowSpecification(
                        WorkflowSpecification.SCHEMA_VERSION, "Ajudas de custo", List.of(request, review), List.of()),
                new Publication("sofia", draft, 1));
        submitted = seed.submit(
                reference,
                draft,
                version,
                request.id(),
                review,
                new RequestData.Requester(
                        "ana", "Ana Ferreira", "Técnica Superior", "Divisão de Obras Municipais", "Rui Matos"),
                List.of());
    }

    @ParameterizedTest
    @CsvSource({
        "bilhete_comboio.pdf, application/pdf, inline",
        "fatura_hotel.png,    image/png,       inline",
        "fatura.html,         application/octet-stream, attachment",
        "logotipo.svg,        application/octet-stream, attachment",
        "notas.txt,           application/octet-stream, attachment"
    })
    void onlyPdfsAndImagesOpenInTheBrowser(String filename, String type, String disposition) throws Exception {
        byte[] bytes = ("Conteúdo de " + filename).getBytes(StandardCharsets.UTF_8);
        UUID file = seed.attach(submitted, filename, bytes);

        mvc.perform(get("/api/engine/procedures/" + reference + "/files/" + file)
                        .header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isOk())
                .andExpect(content().contentType(type))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith(disposition)))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(content().bytes(bytes));
    }

    private static StepSpecification step(String name, String plugin) {
        return new StepSpecification(
                UUID.randomUUID().toString(),
                new PluginRef(plugin, "0.1.0"),
                name,
                "Divisão Financeira",
                null,
                List.of(),
                List.of());
    }
}
