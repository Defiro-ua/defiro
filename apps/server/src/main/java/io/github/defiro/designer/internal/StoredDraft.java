package io.github.defiro.designer.internal;

import java.util.UUID;

/**
 * A draft as the Designer store keeps it.
 *
 * @param revision how many times it was saved, starting at 1
 * @param published its last publication, or null if it was never published
 */
record StoredDraft(UUID id, int revision, WorkflowDraft draft, Published published) {}
