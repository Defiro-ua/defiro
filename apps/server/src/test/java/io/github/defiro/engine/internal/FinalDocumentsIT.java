package io.github.defiro.engine.internal;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.defiro.TestTokens;
import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.identity.Person;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.Transition;
import io.github.defiro.spec.WorkflowSpecification;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// The final document of the closed AC-2026-0098, as the UC23 feature has it: Ana asked, Joana
// validated and closed it, and the document was generated then.
@Import({TestcontainersConfiguration.class, TestTokens.TrustedIssuer.class})
@SpringBootTest
@AutoConfigureMockMvc
class FinalDocumentsIT {

    static final String REFERENCE = "AC-2026-0098";
    static final String FILENAME = "ordem_reembolso_AC-2026-0098.pdf";
    // A unit handles the last step, so the test needs no group ids.
    static final String UNIT = "Recursos Humanos";

    @Autowired
    FinalDocuments finalDocuments;

    @Autowired
    Decisions decisions;

    @Autowired
    ProcedureSeed seed;

    @Autowired
    Deployment deployment;

    @Autowired
    MockMvc mvc;

    final Person ana = new Person("ana", "Ana Ferreira", "Técnica Superior", "Divisão de Obras Municipais", List.of());
    final Person joana = new Person("joana", "Joana Pires", "Técnica de Recursos Humanos", UNIT, List.of());

    byte[] pdf;

    @BeforeEach
    void joanaClosedAnasRequest() throws IOException {
        StepSpecification request = step("Pedido", "submission");
        StepSpecification last = step("Validação final", "final-validation");
        UUID draft = UUID.randomUUID();
        int version = deployment.deploy(
                new WorkflowSpecification(
                        WorkflowSpecification.SCHEMA_VERSION,
                        "Ajudas de custo",
                        List.of(request, last),
                        List.of(
                                new Transition(request.id(), "submitted", last.id()),
                                new Transition(last.id(), Decisions.APPROVED, null))),
                new Publication("sofia", draft, 1));
        ProcedureSeed.Submitted submitted = seed.submit(
                REFERENCE,
                draft,
                version,
                request.id(),
                last,
                new RequestData.Requester(ana.id(), ana.name(), ana.jobTitle(), ana.unit(), "Rui Matos"),
                List.of());
        decisions.take(submitted.task(), joana);
        decisions.approve(submitted.task(), joana);
        seed.closedAt(REFERENCE, OffsetDateTime.parse("2026-10-17T15:30:00+01:00"));
        pdf = fixture();
        seed.generated(REFERENCE, FILENAME, "application/pdf", pdf);
    }

    @Test
    void theRequesterGetsIt() {
        FinalDocuments.Opened opened = finalDocuments.open(REFERENCE, ana).orElseThrow();

        assertThat(opened.document().filename()).isEqualTo(FILENAME);
        assertThat(opened.content()).isEqualTo(pdf);
    }

    // Shown in the browser, as the PDF it is, and kept by no cache: only its participants get it.
    @Test
    void theApiSendsItAsAPdfToShow() throws Exception {
        MvcResult result = mvc.perform(get("/api/engine/procedures/" + REFERENCE + "/document")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TRUSTED_KEY, claims -> claims.subject(ana.id()))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes(pdf))
                .andReturn();

        ContentDisposition disposition =
                ContentDisposition.parse(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertThat(disposition.isInline()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo(FILENAME);
    }

    private static byte[] fixture() throws IOException {
        try (InputStream in = FinalDocumentsIT.class.getResourceAsStream("/fixtures/" + FILENAME)) {
            assertThat(in).isNotNull();
            return in.readAllBytes();
        }
    }

    private static StepSpecification step(String name, String plugin) {
        return new StepSpecification(
                UUID.randomUUID().toString(), new PluginRef(plugin, "0.1.0"), name, UNIT, null, List.of(), List.of());
    }
}
