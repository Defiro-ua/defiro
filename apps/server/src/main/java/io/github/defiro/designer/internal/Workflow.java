package io.github.defiro.designer.internal;

import java.util.List;
import java.util.UUID;

/**
 * A workflow people can make requests with.
 *
 * @param definition the draft it was created from
 */
record Workflow(UUID id, String name, int version, WorkflowStatus status, WorkflowDraft definition) {

    /** The first version of a new workflow, active from the start. */
    static Workflow firstVersionOf(WorkflowDraft draft) {
        List<Problem> problems = draft.problems();
        if (!problems.isEmpty()) {
            throw new IncompleteWorkflowException(problems);
        }
        return new Workflow(UUID.randomUUID(), draft.name(), 1, WorkflowStatus.ACTIVE, draft);
    }
}
