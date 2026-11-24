package io.github.defiro.spec;

import java.util.List;

/**
 * One step of a published workflow.
 *
 * @param handledBy the group or unit that handles the step; for the first step, who can request
 * @param deadline the legal deadline in working days, or null for the first step
 * @param returnReasons why the step may return the request to be corrected
 * @param rejectReasons why the step may reject the request for good
 * @param fields the questions of the request form; only the first step, the request, has them
 */
public record StepSpecification(
        String id,
        PluginRef plugin,
        String name,
        String handledBy,
        Integer deadline,
        List<String> returnReasons,
        List<String> rejectReasons,
        List<FieldSpecification> fields) {

    public StepSpecification {
        returnReasons = List.copyOf(returnReasons);
        rejectReasons = List.copyOf(rejectReasons);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
