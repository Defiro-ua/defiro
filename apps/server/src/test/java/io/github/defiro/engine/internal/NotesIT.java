package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.engine.internal.Notes.Note;
import io.github.defiro.engine.internal.Notes.NoteFrom;
import io.github.defiro.identity.Person;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.Transition;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// The mockup's request and Rui's note on it; the steps are handled by units, so the test needs no
// group ids.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NotesIT {

    static final String NOTE = "Indicou 10/11/2026; o certificado de presença diz 09/11/2026.";
    static final Person RUI =
            new Person("rui", "Rui Matos", "Chefe de Divisão", "Divisão de Obras Municipais", List.of());
    static final Person SEGUNDA =
            new Person("segunda", "Segunda Chefia", "Chefe de Divisão", "Divisão de Obras Municipais", List.of());
    static final Person CARLA =
            new Person("carla", "Carla Dias", "Técnica da Divisão Financeira", "Divisão Financeira", List.of());

    @Autowired
    NoteEditor editor;

    @Autowired
    Decisions decisions;

    @Autowired
    Requests requests;

    @Autowired
    ProcedureSeed seed;

    @Autowired
    Deployment deployment;

    final String reference = "IT-" + UUID.randomUUID().toString().substring(0, 8);
    ProcedureSeed.Submitted submitted;
    UUID task;

    @BeforeEach
    void ruiTookTheRequestAnaSubmitted() {
        StepSpecification request = step("Pedido", "submission", "Todos os trabalhadores");
        StepSpecification review = step("Validação da chefia", "review", "Divisão de Obras Municipais");
        StepSpecification finance = step("Verificação financeira", "final-validation", "Divisão Financeira");
        List<Transition> transitions = List.of(
                new Transition(request.id(), "submitted", review.id()),
                new Transition(review.id(), "approved", finance.id()),
                new Transition(finance.id(), "approved", null));
        UUID draft = UUID.randomUUID();
        int version = deployment.deploy(
                new WorkflowSpecification(
                        WorkflowSpecification.SCHEMA_VERSION,
                        "Ajudas de custo",
                        List.of(request, review, finance),
                        transitions),
                new Publication("sofia", draft, 1));
        submitted = seed.submit(
                reference,
                draft,
                version,
                request.id(),
                review,
                new RequestData.Requester(
                        "ana", "Ana Ferreira", "Técnica Superior", "Divisão de Obras Municipais", "Rui Matos"),
                List.of(new RequestData.Answer("regresso", "Regresso", "10/11/2026 19:40")));
        task = submitted.task();
        decisions.take(task, RUI);
    }

    @Test
    void aNoteOnAnAnswerKeepsItsLabel() {
        assertThat(editor.put(task, "regresso", NOTE, RUI)).contains(new Note("regresso", "Regresso", NOTE));
        assertThat(mine()).containsExactly(new Note("regresso", "Regresso", NOTE));
    }

    @Test
    void aNoteOnAFileIsLabelledWithItsName() {
        UUID file = seed.attach(submitted, "fatura_hotel.pdf", new byte[] {1});

        editor.put(task, Notes.fileField(file), NOTE, RUI);

        assertThat(mine()).containsExactly(new Note(Notes.fileField(file), "fatura_hotel.pdf", NOTE));
    }

    @Test
    void changingANoteReplacesIt() {
        editor.put(task, "regresso", NOTE, RUI);

        editor.put(task, "regresso", "Falta o certificado de presença com o carimbo da entidade formadora.", RUI);

        assertThat(mine())
                .extracting(Note::text)
                .containsExactly("Falta o certificado de presença com o carimbo da entidade formadora.");
    }

    @Test
    void deletingANoteRemovesIt() {
        editor.put(task, "regresso", NOTE, RUI);

        assertThat(editor.delete(task, "regresso", RUI)).isTrue();
        assertThat(mine()).isEmpty();
        assertThat(editor.delete(task, "regresso", RUI))
                .as("a note no longer there")
                .isFalse();
    }

    @Test
    void anEmptyOrTooLongNoteOrOneOnNoFieldIsRefused() {
        assertThatThrownBy(() -> editor.put(task, "regresso", "   ", RUI)).isInstanceOf(InvalidNoteException.class);
        assertThatThrownBy(() -> editor.put(task, "regresso", "x".repeat(1001), RUI))
                .isInstanceOf(InvalidNoteException.class);
        assertThatThrownBy(() -> editor.put(task, "unit", NOTE, RUI)).isInstanceOf(InvalidNoteException.class);
        assertThatThrownBy(() -> editor.put(task, Notes.fileField(UUID.randomUUID()), NOTE, RUI))
                .isInstanceOf(InvalidNoteException.class);
        assertThat(editor.put(task, "regresso", "x".repeat(1000), RUI))
                .as("1000 characters")
                .isPresent();
    }

    @Test
    void onlyWhoTookTheTaskWritesNotes() {
        assertThatThrownBy(() -> editor.put(task, "regresso", NOTE, SEGUNDA)).isInstanceOf(NotAllowedException.class);
    }

    @Test
    void notesNoLongerChangeOnceTheReviewerDecided() {
        editor.put(task, "regresso", NOTE, RUI);
        decisions.approve(task, RUI);

        assertThatThrownBy(() -> editor.put(task, "regresso", "Outra", RUI)).isInstanceOf(NotAllowedException.class);
        assertThatThrownBy(() -> editor.delete(task, "regresso", RUI)).isInstanceOf(NotAllowedException.class);
    }

    @Test
    void untilTheDecisionNobodyElseSeesThem() {
        editor.put(task, "regresso", NOTE, RUI);

        Requests.Request seen = requests.open(reference, SEGUNDA).orElseThrow();
        assertThat(seen.notes()).isEmpty();
        assertThat(seen.notesFrom()).isEmpty();
    }

    @Test
    void approvingSendsThemToTheNextStepAndTheHistoryCountsThem() {
        editor.put(task, "regresso", NOTE, RUI);

        decisions.approve(task, RUI);

        assertThat(requests.open(reference, CARLA).orElseThrow().notesFrom())
                .containsExactly(new NoteFrom("regresso", "Regresso", NOTE, "Rui Matos, Chefe de Divisão"));
        Requests.Request ana = requests.open(reference, requester()).orElseThrow();
        assertThat(ana.notesFrom())
                .as("the requester, while the request goes on")
                .isEmpty();
        assertThat(ana.history())
                .extracting(History.Line::text)
                .contains("Rui Matos, Chefe de Divisão, aprovou o passo «Validação da chefia» com 1 nota");
    }

    @Test
    void undoingGivesThemBackToTheReviewer() {
        editor.put(task, "regresso", NOTE, RUI);
        decisions.approve(task, RUI);

        decisions.undo(task, RUI);

        assertThat(mine()).containsExactly(new Note("regresso", "Regresso", NOTE));
        assertThat(editor.put(task, "regresso", "Outra", RUI)).isPresent();
    }

    @Test
    void releasingTheTaskDiscardsThem() {
        editor.put(task, "regresso", NOTE, RUI);

        decisions.release(task, RUI);
        decisions.take(task, RUI);

        assertThat(mine()).isEmpty();
    }

    private List<Note> mine() {
        return requests.open(reference, RUI).orElseThrow().notes();
    }

    private static Person requester() {
        return new Person("ana", "Ana Ferreira", "Técnica Superior", "Divisão de Obras Municipais", List.of());
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
