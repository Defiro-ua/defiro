package io.github.defiro.engine.internal;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

// «Os meus pedidos» as the UC23 feature has it: Ana asked AC-2026-0098, which Joana closed, and
// AC-2026-0147, which waits at the financial check.
@Import({TestcontainersConfiguration.class, TestTokens.TrustedIssuer.class})
@SpringBootTest
@AutoConfigureMockMvc
class MyRequestsIT {

    static final String CLOSED = "AC-2026-0098";
    static final String OPEN = "AC-2026-0147";
    static final String HEADS_UNIT = "Divisão de Obras Municipais";
    static final String FINANCE_UNIT = "Divisão Financeira";
    static final String UNIT = "Recursos Humanos";

    @Autowired
    Procedures procedures;

    @Autowired
    Decisions decisions;

    @Autowired
    ProcedureSeed seed;

    @Autowired
    Deployment deployment;

    @Autowired
    MockMvc mvc;

    final Person ana =
            new Person("ana-" + UUID.randomUUID(), "Ana Ferreira", "Técnica Superior", HEADS_UNIT, List.of());
    final Person rui = new Person("rui", "Rui Matos", "Chefe de Divisão", HEADS_UNIT, List.of());
    final Person carla = new Person("carla", "Carla Dias", "Técnica da Divisão Financeira", FINANCE_UNIT, List.of());
    final Person joana = new Person("joana", "Joana Pires", "Técnica de Recursos Humanos", UNIT, List.of());

    UUID draft;
    int version;
    StepSpecification request;
    StepSpecification first;

    @BeforeEach
    void anaHasAClosedRequestAndAnOpenOne() {
        request = step("Pedido", "submission", UNIT);
        first = step("Validação da chefia", "review", HEADS_UNIT);
        StepSpecification second = step("Verificação financeira", "review", FINANCE_UNIT);
        StepSpecification last = step("Validação final", "final-validation", UNIT);
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
        Decisions.Approved byRui = approve(submit(CLOSED).task(), rui);
        Decisions.Approved byCarla = approve(byRui.nextTask(), carla);
        approve(byCarla.nextTask(), joana);
        seed.closedAt(CLOSED, OffsetDateTime.parse("2026-10-17T15:30:00+01:00"));
        seed.generated(CLOSED, "ordem_reembolso_AC-2026-0098.pdf", "application/pdf", new byte[] {1});
        approve(submit(OPEN).task(), rui);
    }

    @Test
    void listsOnlyMine() {
        assertThat(procedures.of(ana.id()))
                .extracting(Procedures.Mine::reference)
                .containsExactlyInAnyOrder(CLOSED, OPEN);
        assertThat(procedures.of("nobody-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void newestFirst() {
        assertThat(procedures.of(ana.id()))
                .extracting(Procedures.Mine::reference)
                .containsExactly(OPEN, CLOSED);
    }

    @Test
    void closedWithDocument() {
        Procedures.Mine mine = mine(CLOSED);

        assertThat(mine.workflow()).isEqualTo("Ajudas de custo");
        assertThat(mine.status()).isEqualTo("closed");
        assertThat(mine.closedAt()).isEqualTo(OffsetDateTime.parse("2026-10-17T15:30:00+01:00"));
        assertThat(mine.closedAt()
                        .toInstant()
                        .atZone(ZoneId.of("Europe/Lisbon"))
                        .toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 10, 17, 15, 30));
        assertThat(mine.hasDocument()).isTrue();
    }

    @Test
    void inProgressHasNone() {
        Procedures.Mine mine = mine(OPEN);

        assertThat(mine.currentStep()).isEqualTo("Verificação financeira");
        assertThat(mine.closedAt()).isNull();
        assertThat(mine.hasDocument()).isFalse();
    }

    @Test
    void theApiListsThePersonsOwn() throws Exception {
        mvc.perform(get("/api/engine/my-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TRUSTED_KEY, claims -> claims.subject(ana.id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.reference == '" + CLOSED + "')].hasDocument")
                        .value(true));
        mvc.perform(get("/api/engine/my-requests")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(TRUSTED_KEY, claims -> claims.subject("nobody-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Procedures.Mine mine(String reference) {
        return procedures.of(ana.id()).stream()
                .filter(m -> m.reference().equals(reference))
                .findFirst()
                .orElseThrow();
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
