package io.github.defiro.designer.internal;

import static io.github.defiro.designer.internal.WorkflowDraftTest.complete;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkflowTest {

    @Test
    void aCompleteDraftBecomesAnActiveFirstVersion() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo").with(complete("Validação da chefia"));

        Workflow workflow = Workflow.firstVersionOf(draft);

        assertThat(workflow.name()).isEqualTo("Ajudas de custo");
        assertThat(workflow.version()).isEqualTo(1);
        assertThat(workflow.status()).isEqualTo(WorkflowStatus.ACTIVE);
        assertThat(workflow.definition()).isEqualTo(draft);
    }

    @Test
    void anIncompleteDraftIsRefusedWithItsProblems() {
        WorkflowDraft draft = WorkflowDraft.named("");

        assertThatThrownBy(() -> Workflow.firstVersionOf(draft))
                .isInstanceOfSatisfying(
                        IncompleteWorkflowException.class,
                        refused -> assertThat(refused.problems()).containsExactlyElementsOf(draft.problems()));
    }

    @Test
    void twoWorkflowsFromOneDraftAreTwoWorkflows() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo").with(complete("Validação da chefia"));

        assertThat(Workflow.firstVersionOf(draft).id())
                .isNotEqualTo(Workflow.firstVersionOf(draft).id());
    }
}
