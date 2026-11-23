package io.github.defiro.plugin;

/**
 * Who should act on a step that waits. A rule only describes them: the Engine turns it into the
 * specific people who get the task, when it creates the task.
 */
public sealed interface AssigneeRule {

    /** The person who made the request, for example to correct it after a return. */
    record Requester() implements AssigneeRule {}

    /** The managers of the requester's unit. */
    record ManagersOfRequesterUnit() implements AssigneeRule {}

    /**
     * Everyone in a group or unit of the organization.
     *
     * @param groupId the group's id in defiro
     */
    record Group(String groupId) implements AssigneeRule {

        public Group {
            Require.text(groupId, "A group rule needs the group's id");
        }
    }
}
