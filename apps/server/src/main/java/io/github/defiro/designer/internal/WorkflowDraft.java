package io.github.defiro.designer.internal;

import java.util.List;

/** A workflow as the Workflow Manager is designing it, before it is created. */
record WorkflowDraft(String name) {

    List<Problem> problems() {
        if (name == null || name.isBlank()) {
            return List.of(new Problem("name", "Dê um nome ao workflow."));
        }
        return List.of();
    }
}
