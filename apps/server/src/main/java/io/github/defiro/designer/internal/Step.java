package io.github.defiro.designer.internal;

import io.github.defiro.spec.FieldSpecification;
import io.github.defiro.spec.PluginRef;
import java.util.List;

/**
 * One step of a draft's path, as the browser sends it.
 *
 * @param plugin the step type, from the Plugin Registry's catalogue
 * @param handledBy the group or unit that handles the step; for the request, who can make it
 * @param deadline the legal deadline in working days; the request has none
 * @param fields the questions of the request form (UC04); only the request has them
 */
record Step(
        String id,
        PluginRef plugin,
        String name,
        String handledBy,
        Integer deadline,
        List<String> returnReasons,
        List<String> rejectReasons,
        List<FieldSpecification> fields) {

    Step {
        returnReasons = returnReasons == null ? List.of() : List.copyOf(returnReasons);
        rejectReasons = rejectReasons == null ? List.of() : List.copyOf(rejectReasons);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    Step withPlugin(PluginRef newPlugin) {
        return new Step(id, newPlugin, name, handledBy, deadline, returnReasons, rejectReasons, fields);
    }

    Step withHandledBy(String newHandledBy) {
        return new Step(id, plugin, name, newHandledBy, deadline, returnReasons, rejectReasons, fields);
    }

    Step withDeadline(Integer newDeadline) {
        return new Step(id, plugin, name, handledBy, newDeadline, returnReasons, rejectReasons, fields);
    }

    Step withReasons(List<String> newReturnReasons, List<String> newRejectReasons) {
        return new Step(id, plugin, name, handledBy, deadline, newReturnReasons, newRejectReasons, fields);
    }

    Step withFields(List<FieldSpecification> newFields) {
        return new Step(id, plugin, name, handledBy, deadline, returnReasons, rejectReasons, newFields);
    }
}
