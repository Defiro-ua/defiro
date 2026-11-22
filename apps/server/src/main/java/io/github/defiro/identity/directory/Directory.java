package io.github.defiro.identity.directory;

import java.util.List;

/**
 * The organization's directory: who can handle a step or make a request. The architecture reads it
 * from Entra ID; until defiro does so through Microsoft Graph, it is a configured list.
 */
@FunctionalInterface
public interface Directory {

    /** The groups and units of the organization, in the order the Workflow Manager sees them */
    List<Group> groupsAndUnits();
}
