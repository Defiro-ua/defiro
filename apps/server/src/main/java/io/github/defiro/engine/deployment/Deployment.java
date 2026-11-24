package io.github.defiro.engine.deployment;

import io.github.defiro.spec.WorkflowSpecification;
import java.util.Optional;
import java.util.UUID;

/**
 * Takes a workflow the Designer has validated and keeps it as a new immutable version. New
 * procedures start on the latest version; a procedure already running keeps its own copy.
 */
public interface Deployment {

    /**
     * Stores the specification as the workflow's next version. Safe to retry: the same draft and
     * revision give back the version they already have.
     *
     * @return the version number, starting at 1 for each workflow
     */
    int deploy(WorkflowSpecification specification, Publication publication);

    /**
     * One version of a workflow, exactly as it was deployed: for example to download it as a file.
     *
     * @param draft the Designer's draft, which names the workflow across its versions
     * @return the specification, or empty if the workflow has no such version
     */
    Optional<WorkflowSpecification> version(UUID draft, int version);
}
