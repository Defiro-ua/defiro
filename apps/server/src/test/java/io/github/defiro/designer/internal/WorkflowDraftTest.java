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

    static Step review(String name) {
        return Step.review(name, "Chefias da unidade do requerente", 5);
    }

    static List<String> path(WorkflowDraft draft) {
        return draft.steps().stream().map(Step::name).toList();
    }
}
