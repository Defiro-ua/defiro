package io.github.defiro.designer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorkflowDraftTest {

    static final Problem NO_NAME = new Problem("name", "Dê um nome ao workflow.");

    @Test
    void aWorkflowWithNoNameIsRefusedOnItsName() {
        WorkflowDraft draft = WorkflowDraft.named("");

        assertThat(draft.problems()).containsExactly(NO_NAME);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   ", "\t"})
    void blankOrMissingNameIsNoName(String name) {
        WorkflowDraft draft = WorkflowDraft.named(name);

        assertThat(draft.problems()).containsExactly(NO_NAME);
    }

    @Test
    void aWorkflowWithANameHasNoProblems() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo");

        assertThat(draft.problems()).isEmpty();
    }

    @Test
    void aNewDraftStartsWithTheRequestStep() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo");

        assertThat(path(draft)).containsExactly("Pedido");
        assertThat(draft.steps().getFirst().type()).isEqualTo(StepType.REQUEST);
    }

    @Test
    void aReviewStepJoinsTheEndOfThePath() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(review("Validação da chefia"))
                .with(review("Verificação financeira"));

        assertThat(path(draft)).containsExactly("Pedido", "Validação da chefia", "Verificação financeira");
    }

    @Test
    void renamingADraftKeepsItsPath() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo").with(review("Validação da chefia"));

        WorkflowDraft renamed = draft.withName("");

        assertThat(renamed.name()).isEmpty();
        assertThat(path(renamed)).containsExactly("Pedido", "Validação da chefia");
    }

    @Test
    void aReviewStepMovesUp() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(review("Validação da chefia"))
                .with(review("Verificação financeira"));

        WorkflowDraft moved = draft.move(idOf(draft, "Verificação financeira"), Direction.UP);

        assertThat(path(moved)).containsExactly("Pedido", "Verificação financeira", "Validação da chefia");
    }

    @Test
    void aReviewStepMovesDown() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(review("Validação da chefia"))
                .with(review("Verificação financeira"));

        WorkflowDraft moved = draft.move(idOf(draft, "Validação da chefia"), Direction.DOWN);

        assertThat(path(moved)).containsExactly("Pedido", "Verificação financeira", "Validação da chefia");
    }

    @Test
    void aReviewStepNeverMovesAboveTheRequest() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo").with(review("Validação da chefia"));

        WorkflowDraft moved = draft.move(idOf(draft, "Validação da chefia"), Direction.UP);

        assertThat(path(moved)).containsExactly("Pedido", "Validação da chefia");
    }

    @Test
    void theLastStepDoesNotMoveDown() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo").with(review("Validação da chefia"));

        WorkflowDraft moved = draft.move(idOf(draft, "Validação da chefia"), Direction.DOWN);

        assertThat(path(moved)).containsExactly("Pedido", "Validação da chefia");
    }

    @Test
    void theRequestStepDoesNotMove() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo").with(review("Validação da chefia"));

        WorkflowDraft moved = draft.move(idOf(draft, "Pedido"), Direction.DOWN);

        assertThat(path(moved)).containsExactly("Pedido", "Validação da chefia");
    }

    @Test
    void aReviewStepCanBeRemoved() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(review("Validação da chefia"))
                .with(review("Verificação financeira"));

        WorkflowDraft removed = draft.without(idOf(draft, "Verificação financeira"));

        assertThat(path(removed)).containsExactly("Pedido", "Validação da chefia");
    }

    @Test
    void theRequestStepIsNeverRemoved() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo");

        WorkflowDraft removed = draft.without(idOf(draft, "Pedido"));

        assertThat(path(removed)).containsExactly("Pedido");
    }

    @Test
    void theFinalValidationGoesLast() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(review("Validação da chefia"))
                .with(Step.finalValidation());

        assertThat(path(draft)).containsExactly("Pedido", "Validação da chefia", "Validação final");
        assertThat(draft.steps().getLast().type()).isEqualTo(StepType.FINAL_VALIDATION);
    }

    @Test
    void thereIsOnlyOneFinalValidation() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(Step.finalValidation())
                .with(Step.finalValidation());

        assertThat(path(draft)).containsExactly("Pedido", "Validação final");
    }

    @Test
    void aReviewStepAddedAfterTheFinalValidationGoesBeforeIt() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(Step.finalValidation())
                .with(review("Validação da chefia"));

        assertThat(path(draft)).containsExactly("Pedido", "Validação da chefia", "Validação final");
    }

    @Test
    void theFinalValidationDoesNotMove() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(review("Validação da chefia"))
                .with(Step.finalValidation());

        WorkflowDraft moved = draft.move(idOf(draft, "Validação final"), Direction.UP);

        assertThat(path(moved)).containsExactly("Pedido", "Validação da chefia", "Validação final");
    }

    @Test
    void aReviewStepDoesNotMovePastTheFinalValidation() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(review("Validação da chefia"))
                .with(Step.finalValidation());

        WorkflowDraft moved = draft.move(idOf(draft, "Validação da chefia"), Direction.DOWN);

        assertThat(path(moved)).containsExactly("Pedido", "Validação da chefia", "Validação final");
    }

    static String idOf(WorkflowDraft draft, String name) {
        return draft.steps().stream()
                .filter(step -> step.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
    }

    static Step review(String name) {
        return Step.review(name, "Chefias da unidade do requerente", 5);
    }

    static List<String> path(WorkflowDraft draft) {
        return draft.steps().stream().map(Step::name).toList();
    }
}
