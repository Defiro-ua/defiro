package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.identity.Person;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.Transition;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DecisionsIT {

    // A unit handles the steps, so the test needs no group ids.
    static final String UNIT = "Divisão Financeira";

    @Autowired
    Decisions decisions;

    @Autowired
    ProcedureSeed seed;

    @Autowired
    Deployment deployment;

    @Autowired
    JdbcClient jdbc;

    static final RequestData.Requester ANA = new RequestData.Requester(
            "ana", "Ana Ferreira", "Técnica Superior", "Divisão de Obras Municipais", "Rui Matos");

    final String reference = "IT-" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void approvingTheLastStepClosesTheProcedure() {
        ProcedureSeed.Submitted submitted =
                submit(step("Pedido", "submission"), step("Validação final", "final-validation"));

        assertThat(decisions.approve(submitted.task(), person("carla")))
                .hasValueSatisfying(approved -> assertThat(approved.nextStep()).isNull());
        assertThat(jdbc.sql("select status from engine.procedure where id = :id")
                        .param("id", submitted.procedure())
                        .query(String.class)
                        .single())
                .isEqualTo("closed");
    }

    @Test
    void undoingTheApprovalOfTheLastStepOpensTheProcedureAgain() {
        ProcedureSeed.Submitted submitted =
                submit(step("Pedido", "submission"), step("Validação final", "final-validation"));
        decisions.approve(submitted.task(), person("carla"));

        assertThat(decisions.undo(submitted.task(), person("carla"))).isPresent();
        assertThat(jdbc.sql("select status from engine.procedure where id = :id")
                        .param("id", submitted.procedure())
                        .query(String.class)
                        .single())
                .isEqualTo("in_progress");
    }

    @Test
    void aTaskNobodyApprovedHasNothingToUndo() {
        ProcedureSeed.Submitted submitted =
                submit(step("Pedido", "submission"), step("Validação final", "final-validation"));

        assertThatThrownBy(() -> decisions.undo(submitted.task(), person("carla")))
                .isInstanceOf(NotAllowedException.class);
    }

    @Test
    void anUndoneApprovalCanBeMadeAgain() {
        ProcedureSeed.Submitted submitted =
                submit(step("Pedido", "submission"), step("Validação final", "final-validation"));
        decisions.approve(submitted.task(), person("carla"));
        decisions.undo(submitted.task(), person("carla"));

        assertThat(decisions.approve(submitted.task(), person("carla"))).isPresent();
    }

    @Test
    void anApprovalIsNoLongerUndoneOnceTheRequestCameBackAndWasApprovedAgain() {
        StepSpecification first = step("Validação da chefia", "review");
        StepSpecification second = step("Verificação financeira", "final-validation");
        ProcedureSeed.Submitted submitted = submit(step("Pedido", "submission"), first, second);
        decisions.approve(submitted.task(), person("rui"));
        // Corrected and resubmitted (UC18, UC20): back to the first review, where Segunda approves
        ProcedureSeed.Resubmitted resubmitted = seed.resubmit(submitted, ANA, List.of(), Map.of(), first);
        UUID secondsNextTask = decisions
                .approve(resubmitted.request().task(), person("segunda"))
                .orElseThrow()
                .nextTask();

        assertThatThrownBy(() -> decisions.undo(submitted.task(), person("rui")))
                .isInstanceOf(NoLongerUndoableException.class);
        assertThat(jdbc.sql("select status from engine.task where id = :id")
                        .param("id", secondsNextTask)
                        .query(String.class)
                        .single())
                .as("the task Segunda's approval opened")
                .isEqualTo("open");
    }

    @Test
    void thereIsNoDecisionOnAnUnknownTask() {
        assertThat(decisions.approve(UUID.randomUUID(), person("carla"))).isEmpty();
    }

    @Test
    void ofTwoPeopleApprovingAtTheSameTimeOnlyOneDoes() throws Exception {
        ProcedureSeed.Submitted submitted =
                submit(step("Pedido", "submission"), step("Validação final", "final-validation"));
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> approve = () -> {
            start.await();
            try {
                return decisions
                        .approve(submitted.task(), person(UUID.randomUUID().toString()))
                        .isPresent();
            } catch (AlreadyDecidedException late) {
                return false;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(approve);
            Future<Boolean> second = pool.submit(approve);
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.sql("select count(*) from engine.audit_log where action = 'approved' and entity_id = :task")
                        .param("task", submitted.task().toString())
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    private ProcedureSeed.Submitted submit(StepSpecification request, StepSpecification... reviews) {
        List<StepSpecification> steps = new ArrayList<>(List.of(request));
        steps.addAll(List.of(reviews));
        // As the Designer gives them: approved goes on, returned goes back to the request.
        List<Transition> transitions = new ArrayList<>();
        transitions.add(new Transition(request.id(), "submitted", reviews[0].id()));
        for (int k = 1; k < steps.size(); k++) {
            String next = k + 1 < steps.size() ? steps.get(k + 1).id() : null;
            transitions.add(new Transition(steps.get(k).id(), "approved", next));
            transitions.add(new Transition(steps.get(k).id(), "returned", request.id()));
            transitions.add(new Transition(steps.get(k).id(), "rejected", null));
        }
        UUID draft = UUID.randomUUID();
        int version = deployment.deploy(
                new WorkflowSpecification(WorkflowSpecification.SCHEMA_VERSION, "Ajudas de custo", steps, transitions),
                new Publication("sofia", draft, 1));
        return seed.submit(reference, draft, version, request.id(), reviews[0], ANA, List.of());
    }

    private static StepSpecification step(String name, String plugin) {
        return new StepSpecification(
                UUID.randomUUID().toString(), new PluginRef(plugin, "0.1.0"), name, UNIT, null, List.of(), List.of());
    }

    private static Person person(String id) {
        return new Person(id, id, "Técnica da Divisão Financeira", UNIT, List.of());
    }
}
