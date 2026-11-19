package io.github.defiro.designer.workflows;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The versions in force: what the Engine starts new procedures on («Novo pedido», UC13). A
 * version never changes; publishing again makes a new one, and procedures already running keep
 * the copy they started with.
 */
public interface PublishedWorkflows {

    /** The latest version of this workflow, or empty if it was never published. */
    Optional<PublishedWorkflow> latest(UUID workflow);

    /** The latest version of each published workflow. */
    List<PublishedWorkflow> inForce();
}
