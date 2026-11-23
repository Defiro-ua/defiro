package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.identity.Person;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.Transition;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

// What the UC18 scenarios do not reach: races, late reviewers, and who sees what afterwards.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReturnAndRejectIT {

    static final String INCORRECT = "Preenchido incorretamente";
    static final String MISSING = "Falta documento comprovativo";
    static final String LATE = "Fora do prazo legal";
    static final String MESSAGE = "Corrija a data de regresso e junte o certificado, por favor.";
    static final String NOTE = "Falta o certificado de presença com o carimbo da entidade formadora.";

    static final String UNIT = "Divisão de Obras Municipais";
    static final Person RUI = new Person("rui", "Rui Matos", "Chefe de Divisão", UNIT, List.of());
    static final Person SEGUNDA = new Person("segunda", "Segunda Chefia", "Chefe de Divisão", UNIT, List.of());
    static final Person ANA = new Person("ana", "Ana Ferreira", "Técnica Superior", "Outra unidade", List.of());
    static final RequestData.Requester REQUESTER =
            new RequestData.Requester("ana", "Ana Ferreira", "Técnica Superior", UNIT, "Rui Matos");

    @Autowired
    Decisions decisions;

    @Autowired
    Requests requests;

    @Autowired
    NoteEditor editor;

    @Autowired
    ProcedureSeed seed;

    @Autowired
    Deployment deployment;

    @Autowired
    JdbcClient jdbc;

    final String reference = "IT-" + UUID.randomUUID().toString().substring(0, 8);
    StepSpecification request;
    StepSpecification review;
    ProcedureSeed.Submitted submitted;
    UUID task;

    @BeforeEach
    void ruiTookTheRequestAnaSubmitted() {
        request = step("Pedido", "submission", List.of(), List.of());
        review = step("Validação da chefia", "final-validation", List.of(INCORRECT, MISSING), List.of(LATE));
        List<Transition> transitions = List.of(
                new Transition(request.id(), "submitted", review.id()),
                new Transition(review.id(), "approved", null),
                new Transition(review.id(), "returned", request.id()),
                new Transition(review.id(), "rejected", null));
        UUID draft = UUID.randomUUID();
        int version = deployment.deploy(
                new WorkflowSpecification(
                        WorkflowSpecification.SCHEMA_VERSION, "Ajudas de custo", List.of(request, review), transitions),
                new Publication("sofia", draft, 1));
        submitted = seed.submit(
                reference,
                draft,
                version,
                request.id(),
                review,
                REQUESTER,
                List.of(new RequestData.Answer("cert", "Datas do certificado", "08/11/2026 a 09/11/2026")));
        task = submitted.task();
        decisions.take(task, RUI);
    }

    @Test
    void reasonsAreKeptOnceInTheOrderOfTheWorkflow() {
        decisions.returnToRequester(task, List.of(MISSING, INCORRECT, MISSING), MESSAGE, RUI);

        assertThat(asAna().decision().reasons()).containsExactly(INCORRECT, MISSING);
    }

    @Test
    void aReasonOfTheListDoesNotCoverOneThatIsNot() {
        assertThatThrownBy(() -> decisions.returnToRequester(task, List.of(INCORRECT, LATE), MESSAGE, RUI))
                .isInstanceOf(InvalidDecisionException.class);
        assertThat(asAna().status()).isEqualTo("in_progress");
    }

    @Test
    void noListOfReasonsIsNoReason() {
        assertThatThrownBy(() -> decisions.reject(task, null, MESSAGE, RUI))
                .isInstanceOf(InvalidDecisionException.class);
    }

    @Test
    void aMessageOfBlanksIsNoMessage() {
        assertThatThrownBy(() -> decisions.returnToRequester(task, List.of(INCORRECT), " \n ", RUI))
                .isInstanceOf(InvalidDecisionException.class);
        assertThatThrownBy(() -> decisions.reject(task, List.of(LATE), null, RUI))
                .isInstanceOf(InvalidDecisionException.class);
    }

    @Test
    void aMessageMayHaveAThousandCharacters() {
        assertThat(decisions.reject(task, List.of(LATE), "x".repeat(1000), RUI)).isPresent();
    }

    @Test
    void aRefusedDecisionLeavesTheTaskAndItsNotesAsTheyWere() {
        editor.put(task, "cert", NOTE, RUI);

        assertThatThrownBy(() -> decisions.reject(task, List.of(LATE), "", RUI))
                .isInstanceOf(InvalidDecisionException.class);

        Requests.Request seen = requests.open(reference, RUI).orElseThrow();
        assertThat(seen.task()).isEqualTo(task);
        assertThat(seen.notes()).extracting(Notes.Note::text).containsExactly(NOTE);
    }

    @Test
    void anUntakenTaskIsNeitherReturnedNorRejected() {
        decisions.release(task, RUI);

        assertThatThrownBy(() -> decisions.returnToRequester(task, List.of(INCORRECT), MESSAGE, RUI))
                .isInstanceOf(NotAllowedException.class);
        assertThatThrownBy(() -> decisions.reject(task, List.of(LATE), MESSAGE, RUI))
                .isInstanceOf(NotAllowedException.class);
    }

    @Test
    void thereIsNothingToReturnOrRejectOnAnUnknownTask() {
        assertThat(decisions.returnToRequester(UUID.randomUUID(), List.of(INCORRECT), MESSAGE, RUI))
                .isEmpty();
        assertThat(decisions.reject(UUID.randomUUID(), List.of(LATE), MESSAGE, RUI))
                .isEmpty();
    }

    @Test
    void whoComesLateIsToldWhoReturnedOrRejected() {
        decisions.returnToRequester(task, List.of(INCORRECT), MESSAGE, RUI);

        assertThatThrownBy(() -> decisions.approve(task, SEGUNDA))
                .isInstanceOf(AlreadyDecidedException.class)
                .hasMessage("Este pedido já foi tratado por Rui Matos, Chefe de Divisão.");
    }

    @Test
    void whoComesLateIsToldTheDecisionThatStandsNotAnUndoneOne() {
        decisions.approve(task, RUI);
        decisions.undo(task, RUI);
        decisions.release(task, RUI);
        decisions.take(task, SEGUNDA);
        decisions.reject(task, List.of(LATE), MESSAGE, SEGUNDA);

        assertThatThrownBy(() -> decisions.reject(task, List.of(LATE), MESSAGE, RUI))
                .isInstanceOf(AlreadyDecidedException.class)
                .hasMessage("Este pedido já foi tratado por Segunda Chefia, Chefe de Divisão.");
    }

    @Test
    void ofTwoDecisionsAtTheSameTimeOnlyOneCounts() throws Exception {
        // A double click on «Rejeitar o pedido»
        List<Boolean> rejected = race(() -> {
            try {
                return decisions.reject(task, List.of(LATE), MESSAGE, RUI).isPresent();
            } catch (AlreadyDecidedException late) {
                return false;
            }
        });

        assertThat(rejected).containsExactlyInAnyOrder(true, false);
        assertThat(entries("rejected")).isEqualTo(1);
    }

    @Test
    void ofTwoUndosAtTheSameTimeOnlyOneCounts() throws Exception {
        decisions.returnToRequester(task, List.of(INCORRECT), MESSAGE, RUI);

        List<Boolean> undone = race(() -> {
            try {
                return decisions.undo(task, RUI).isPresent();
            } catch (NotAllowedException | NoLongerUndoableException late) {
                return false;
            }
        });

        assertThat(undone).containsExactlyInAnyOrder(true, false);
        assertThat(entries("undone")).isEqualTo(1);
    }

    @Test
    void aRejectionClosesTheProcedureAndUndoingItOpensItAgain() {
        decisions.reject(task, List.of(LATE), MESSAGE, RUI);
        assertThat(closed()).isTrue();

        decisions.undo(task, RUI);
        assertThat(closed()).isFalse();
        assertThat(asAna().status()).isEqualTo("in_progress");
    }

    @Test
    void anUndoneReturnCanBeFollowedByAnotherDecision() {
        decisions.returnToRequester(task, List.of(INCORRECT), MESSAGE, RUI);
        decisions.undo(task, RUI);

        assertThat(decisions.reject(task, List.of(LATE), MESSAGE, RUI)).isPresent();
        assertThat(asAna().decision().reasons()).containsExactly(LATE);
    }

    @Test
    void aReturnIsNoLongerUndoneOnceTheRequestCameBackAndWasReturnedAgain() {
        decisions.returnToRequester(task, List.of(INCORRECT), MESSAGE, RUI);
        UUID again = seed.resubmit(submitted, REQUESTER, List.of(), Map.of(), review)
                .request()
                .task();
        decisions.take(again, SEGUNDA);
        decisions.returnToRequester(again, List.of(MISSING), MESSAGE, SEGUNDA);

        assertThatThrownBy(() -> decisions.undo(task, RUI))
                .isInstanceOf(NoLongerUndoableException.class)
                .hasMessage("Já não é possível desfazer: o pedido já foi tratado no passo «Pedido».");
        assertThat(asAna().status()).isEqualTo("returned");
    }

    @Test
    void aResubmittedRequestGoesOnWithNoDecisionToShow() {
        decisions.returnToRequester(task, List.of(INCORRECT), MESSAGE, RUI);
        seed.resubmit(submitted, REQUESTER, List.of(), Map.of(), review);

        assertThat(asAna().status()).isEqualTo("in_progress");
        assertThat(asAna().decision()).isNull();
    }

    @Test
    void theRequesterSeesWhoReturnedItWhyAndTheMessage() {
        decisions.returnToRequester(task, List.of(INCORRECT), MESSAGE, RUI);

        assertThat(asAna().decision())
                .isEqualTo(new Requests.Decision("Rui Matos, Chefe de Divisão", List.of(INCORRECT), MESSAGE));
        assertThat(asAna().mine()).isTrue();
    }

    @Test
    void theOthersSeeWhoDecidedButTheRequestIsNotTheirs() {
        decisions.reject(task, List.of(LATE), MESSAGE, RUI);

        Requests.Request seen = requests.open(reference, RUI).orElseThrow();
        assertThat(seen.decision().by()).isEqualTo("Rui Matos, Chefe de Divisão");
        assertThat(seen.mine()).isFalse();
    }

    @Test
    void theReviewerSeesTheReasonsOfEachStep() {
        Requests.Request seen = requests.open(reference, RUI).orElseThrow();

        assertThat(seen.path())
                .extracting(Requests.Step::returnReasons, Requests.Step::rejectReasons)
                .containsExactly(tuple(List.of(), List.of()), tuple(List.of(INCORRECT, MISSING), List.of(LATE)));
    }

    private Requests.Request asAna() {
        return requests.open(reference, ANA).orElseThrow();
    }

    private boolean closed() {
        return jdbc.sql("select closed_at is not null from engine.procedure where id = :id")
                .param("id", submitted.procedure())
                .query(Boolean.class)
                .single();
    }

    private int entries(String action) {
        return jdbc.sql("select count(*) from engine.audit_log where action = :action and entity_id = :task")
                .param("action", action)
                .param("task", task.toString())
                .query(Integer.class)
                .single();
    }

    // Runs the same action in two threads at once, and gives what each returned
    private static List<Boolean> race(Callable<Boolean> action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> atOnce = () -> {
            start.await();
            return action.call();
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(atOnce);
            Future<Boolean> second = pool.submit(atOnce);
            start.countDown();
            return List.of(first.get(), second.get());
        } finally {
            pool.shutdown();
        }
    }

    private static StepSpecification step(
            String name, String plugin, List<String> returnReasons, List<String> rejectReasons) {
        return new StepSpecification(
                UUID.randomUUID().toString(),
                new PluginRef(plugin, "0.1.0"),
                name,
                UNIT,
                null,
                returnReasons,
                rejectReasons);
    }
}
