package io.github.defiro.designer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorkflowDraftTest {

    static final Problem NO_NAME = new Problem("name", "Dê um nome ao workflow.");

    @Test
    void aWorkflowWithNoNameIsRefusedOnItsName() {
        WorkflowDraft draft = new WorkflowDraft("");

        assertThat(draft.problems()).containsExactly(NO_NAME);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   ", "\t"})
    void blankOrMissingNameIsNoName(String name) {
        WorkflowDraft draft = new WorkflowDraft(name);

        assertThat(draft.problems()).containsExactly(NO_NAME);
    }

    @Test
    void aWorkflowWithANameHasNoProblems() {
        WorkflowDraft draft = new WorkflowDraft("Ajudas de custo");

        assertThat(draft.problems()).isEmpty();
    }
}
