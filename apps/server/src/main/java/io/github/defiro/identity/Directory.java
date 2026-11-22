package io.github.defiro.identity;

import java.util.Optional;

/**
 * The organization's directory in Entra ID, as the Engine needs it to find who handles a step
 * («resolves assignees» in the architecture).
 *
 * <p>TEMPORARY, for UC05 (Candeias): the Designer keeps the name of the group that handles a step,
 * but tokens carry each group's object id. Until UC05 keeps the group's id in the specification,
 * this finds the id from the name. Once it does, the Engine uses that id and this goes away.
 */
public interface Directory {

    /** The object id of the Entra ID group with this name, or empty if it is not a known group. */
    Optional<String> groupId(String name);
}
