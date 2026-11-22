package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.identity.Person;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RequestsIT {

    @Autowired
    Requests requests;

    @Autowired
    Decisions decisions;

    @Autowired
    ProcedureSeed seed;

    @Autowired
    Deployment deployment;

    @Autowired
    JdbcClient jdbc;

    final String reference = "IT-" + UUID.randomUUID().toString().substring(0, 8);
    final RequestData.Requester ana = new RequestData.Requester(
            "ana", "Ana Ferreira", "Técnica Superior", "Divisão de Obras Municipais", "Rui Matos");

    ProcedureSeed.Submitted submitted;

    @BeforeEach
    void anaSubmittedARequest() {
        StepSpecification request = step("Pedido", "submission", "Todos os trabalhadores");
        // A unit, so the test needs no group ids.
        StepSpecification review = step("Verificação financeira", "review", "Divisão Financeira");
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
                ana,
                List.of(new RequestData.Answer("destino", "Destino", "Lisboa")));
    }

    @Test
    void theRequesterOpensTheirRequest() {
        assertThat(requests.open(reference, person("ana", "Divisão de Obras Municipais")))
                .hasValueSatisfying(opened -> assertThat(opened.answers())
                        // Never resubmitted, so nothing is marked as changed.
                        .containsExactly(new Requests.Answer("destino", "Destino", "Lisboa", false, null)));
    }

    @Test
    void aCandidateOfTheCurrentStepOpensIt() {
        assertThat(requests.open(reference, person("carla", "Divisão Financeira")))
                .hasValueSatisfying(opened -> {
                    assertThat(opened.task()).as("a task she has not taken yet").isNull();
                    assertThat(opened.reviewer()).isNull();
                });
    }

    @Test
    void aCandidateWhoTookTheTaskMayDecideIt() {
        decisions.take(submitted.task(), carla());

        assertThat(requests.open(reference, carla()))
                .hasValueSatisfying(opened ->
                        assertThat(opened.task()).as("the task she may decide").isEqualTo(submitted.task()));
    }

    @Test
    void theOtherCandidatesSeeWhoTookTheTaskAndMayNotDecideIt() {
        decisions.take(submitted.task(), carla());

        assertThat(requests.open(reference, person("outra", "Divisão Financeira")))
                .hasValueSatisfying(opened -> {
                    assertThat(opened.task()).isNull();
                    assertThat(opened.reviewer()).isEqualTo("Carla Dias, Técnica da Divisão Financeira");
                });
    }

    @Test
    void theRequesterHasNoTaskToDecide() {
        assertThat(requests.open(reference, person("ana", "Divisão de Obras Municipais")))
                .hasValueSatisfying(opened -> assertThat(opened.task()).isNull());
    }

    @Test
    void someoneWhoActedOnItBeforeOpensIt() {
        jdbc.sql("""
                        insert into engine.audit_log (actor_id, procedure_id, action, entity_type, entity_id, details)
                        values ('rui', :procedure, 'approved', 'task', 'any', '{}')""").param("procedure", submitted.procedure()).update();

        assertThat(requests.open(reference, person("rui", "Divisão de Obras Municipais")))
                .isPresent();
    }

    @Test
    void anyoneElseIsNotAllowed() {
        Person sofia = person("sofia", "Modernização Administrativa");

        assertThatThrownBy(() -> requests.open(reference, sofia)).isInstanceOf(NotAllowedException.class);
    }

    @Test
    void theRequestIsTheDocumentOfTheFirstStepWhateverOtherDocumentsTheProcedureHas() {
        // A later step's document, older than the request's last change (as after a resubmission)
        jdbc.sql("""
                        insert into engine.document (id, procedure_id, step_id, data, status, updated_at)
                        values (:id, :procedure, 'a-later-step', '{"notes": []}', 'draft', now() - interval '1 day')""")
                .param("id", UUID.randomUUID())
                .param("procedure", submitted.procedure())
                .update();

        assertThat(requests.open(reference, person("carla", "Divisão Financeira")))
                .hasValueSatisfying(opened -> {
                    assertThat(opened.requester().name()).isEqualTo("Ana Ferreira");
                    assertThat(opened.path()).hasSize(2);
                });
    }

    @Test
    void thereIsNoRequestWithAnUnknownReference() {
        assertThat(requests.open("IT-unknown", person("ana", "Divisão de Obras Municipais")))
                .isEmpty();
    }

    @Test
    void aFileOfAnotherRequestIsNotFoundThroughThisOne() {
        UUID elsewhere = UUID.randomUUID();
        jdbc.sql("""
                        insert into engine.procedure (id, reference, draft, version, requester_id, status)
                        select :id, :other, draft, version, requester_id, status from engine.procedure
                        where reference = :reference""")
                .param("id", elsewhere)
                .param("other", reference + "-b")
                .param("reference", reference)
                .update();
        UUID file = seed.attach(new ProcedureSeed.Submitted(elsewhere, null, null), "fatura_hotel.pdf", new byte[] {1});

        assertThat(requests.file(reference, file, person("ana", "Divisão de Obras Municipais")))
                .isEmpty();
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

    private static Person carla() {
        return new Person("carla", "Carla Dias", "Técnica da Divisão Financeira", "Divisão Financeira", List.of());
    }

    private static Person person(String id, String unit) {
        return new Person(id, id, "Técnico", unit, List.of());
    }
}
