package io.github.defiro.designer.internal;

import java.util.UUID;

/**
 * A draft as the Designer store keeps it.
 *
 * @param revision how many times it was saved, starting at 1
 * @param publishedVersion the Engine's version of its last published revision, or null
 */
record StoredDraft(UUID id, int revision, WorkflowDraft draft, Integer publishedVersion) {}
