package io.github.defiro.designer.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * A workflow as the Workflow Manager is designing it, before it is created. Every change gives a
 * new draft, so a draft never changes under whoever holds it.
 */
record WorkflowDraft(String name, List<Step> steps) {

    WorkflowDraft {
        steps = List.copyOf(steps);
    }

    /** A new draft: its path holds only the request step. */
    static WorkflowDraft named(String name) {
        return new WorkflowDraft(name, List.of(Step.request()));
    }

    WorkflowDraft withName(String newName) {
        return new WorkflowDraft(newName, steps);
    }

    WorkflowDraft with(Step step) {
        List<Step> path = new ArrayList<>(steps);
        path.add(step);
        return new WorkflowDraft(name, path);
    }

    List<Problem> problems() {
        if (name == null || name.isBlank()) {
            return List.of(new Problem("name", "Dê um nome ao workflow."));
        }
        return List.of();
    }
}
