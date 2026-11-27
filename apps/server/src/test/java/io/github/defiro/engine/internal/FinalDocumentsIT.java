package io.github.defiro.engine.internal;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    // Units handle the steps, so the test needs no group ids.
    static final String UNIT = "Recursos Humanos";
    static final String HEADS_UNIT = "Divisão de Obras Municipais";
    static final String FINANCE_UNIT = "Divisão Financeira";

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
    final Person rui = new Person("rui", "Rui Matos", "Chefe de Divisão", HEADS_UNIT, List.of());
    final Person carla = new Person("carla", "Carla Dias", "Técnica da Divisão Financeira", FINANCE_UNIT, List.of());
    final Person sofia =
            new Person("sofia", "Sofia Almeida", "Gestora de workflows", "Modernização Administrativa", List.of());
    final Person joana = new Person("joana", "Joana Pires", "Técnica de Recursos Humanos", UNIT, List.of());

    byte[] pdf;
    UUID draft;
    int version;
    StepSpecification request;
    StepSpecification first;
    StepSpecification second;
    StepSpecification last;
    Decisions.Approved closing;

    @BeforeEach
    void joanaClosedAnasRequest() throws IOException {
        request = step("Pedido", "submission");
        first = step("Validação da chefia", "review", HEADS_UNIT);
        second = step("Verificação financeira", "review", FINANCE_UNIT);
        last = step("Validação final", "final-validation", UNIT);
        draft = UUID.randomUUID();
        version = deployment.deploy(
                new WorkflowSpecification(
                        WorkflowSpecification.SCHEMA_VERSION,
                        "Ajudas de custo",
                        List.of(request, first, second, last),
                        List.of(
                                new Transition(request.id(), "submitted", first.id()),
                                new Transition(first.id(), Decisions.APPROVED, second.id()),
                                new Transition(second.id(), Decisions.APPROVED, last.id()),
                                new Transition(last.id(), Decisions.APPROVED, null))),
                new Publication("sofia", draft, 1));
        ProcedureSeed.Submitted submitted = submit(REFERENCE);
        Decisions.Approved byRui = approve(submitted.task(), rui);
        Decisions.Approved byCarla = approve(byRui.nextTask(), carla);
        closing = approve(byCarla.nextTask(), joana);
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

    // Whatever else is stored is never shown inline: only a PDF of defiro's own making is.
    @Test
    void aStoredTypeThatIsNotPdfIsNotServed() throws Exception {
        String other = "AC-2026-0148";
        Decisions.Approved byRui = approve(submit(other).task(), rui);
        Decisions.Approved byCarla = approve(byRui.nextTask(), carla);
        approve(byCarla.nextTask(), joana);
        seed.closedAt(other, OffsetDateTime.parse("2026-10-17T15:30:00+01:00"));
        seed.generated(other, "pagina.html", "text/html", "<script>alert(1)</script>".getBytes());

        mvc.perform(get("/api/engine/procedures/" + other + "/document")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TRUSTED_KEY, claims -> claims.subject(ana.id()))))
                .andExpect(status().isNotFound());
    }

    // Not closed yet: waiting at the last step, with a stored document all the same.
    @Test
    void notClosedHasNoDocument() {
        String open = "AC-2026-0147";
        approve(submit(open).task(), rui);
        seed.generated(open, "ordem_reembolso_AC-2026-0147.pdf", "application/pdf", pdf);

        assertThat(finalDocuments.open(open, ana)).isEmpty();
    }

    @Test
    void undoneClosingHasNoDocument() {
        decisions.undo(closing.task(), joana).orElseThrow();

        assertThat(finalDocuments.open(REFERENCE, ana)).isEmpty();
    }

    @Test
    void whoClosedItGetsIt() {
        FinalDocuments.Opened opened = finalDocuments.open(REFERENCE, joana).orElseThrow();

        assertThat(opened.document().filename()).isEqualTo(FILENAME);
        assertThat(opened.content()).isEqualTo(pdf);
    }

    // Rui and Carla approved earlier steps, which doesn't make the document theirs.
    @Test
    void earlierReviewersAreRefused() {
        assertThatThrownBy(() -> finalDocuments.open(REFERENCE, rui)).isInstanceOf(NotAllowedException.class);
        assertThatThrownBy(() -> finalDocuments.open(REFERENCE, carla)).isInstanceOf(NotAllowedException.class);
    }

    @Test
    void someoneElseIsRefused() {
        assertThatThrownBy(() -> finalDocuments.open(REFERENCE, sofia)).isInstanceOf(NotAllowedException.class);
    }

    @Test
    void anUnknownReferenceIsNotThere() {
        assertThat(finalDocuments.open("AC-2026-9999", sofia)).isEmpty();
    }

    private Decisions.Approved approve(UUID task, Person person) {
        decisions.take(task, person);
        return decisions.approve(task, person).orElseThrow();
    }

    private ProcedureSeed.Submitted submit(String reference) {
        return seed.submit(
                reference,
                draft,
                version,
                request.id(),
                first,
                new RequestData.Requester(ana.id(), ana.name(), ana.jobTitle(), ana.unit(), "Rui Matos"),
                List.of());
    }

    private static byte[] fixture() throws IOException {
        try (InputStream in = FinalDocumentsIT.class.getResourceAsStream("/fixtures/" + FILENAME)) {
            assertThat(in).isNotNull();
            return in.readAllBytes();
        }
    }

    private static StepSpecification step(String name, String plugin) {
        return step(name, plugin, UNIT);
    }

    private static StepSpecification step(String name, String plugin, String handledBy) {
        return new StepSpecification(
                UUID.randomUUID().toString(),
                new PluginRef(plugin, "0.1.0"),
                name,
                handledBy,
                null,
                List.of(),
                List.of());
    }
}
