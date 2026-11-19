package io.github.defiro.designer.internal;

import java.util.List;

/** A draft with missing or invalid answers cannot become a workflow. */
class IncompleteWorkflowException extends RuntimeException {

    private final transient List<Problem> problems;

    IncompleteWorkflowException(List<Problem> problems) {
        super("The workflow has " + problems.size() + " missing or invalid answers");
        this.problems = List.copyOf(problems);
    }

    List<Problem> problems() {
        return problems;
    }
}
