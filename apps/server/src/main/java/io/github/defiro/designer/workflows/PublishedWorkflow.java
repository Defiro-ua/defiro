package io.github.defiro.designer.workflows;

import io.github.defiro.spec.WorkflowSpecification;
import java.util.UUID;

/**
 * One version of a published workflow.
 *
 * @param workflow the workflow, the same across its versions
 * @param version its number, starting at 1 for each workflow
 */
public record PublishedWorkflow(UUID workflow, int version, WorkflowSpecification specification) {}
