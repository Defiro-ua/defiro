package io.github.defiro.spec;

import java.util.List;

/**
 * A workflow as the Workflow Designer publishes it and the Workflow Engine runs it. The Engine
 * keeps each one as an immutable version.
 *
 * @param schemaVersion the version of this format
 * @param steps the path, starting with the request
 */
public record WorkflowSpecification(
        int schemaVersion, String name, List<StepSpecification> steps, List<Transition> transitions) {

    public static final int SCHEMA_VERSION = 1;

    public WorkflowSpecification {
        steps = List.copyOf(steps);
        transitions = List.copyOf(transitions);
    }
}
