package io.github.defiro.designer.internal;

import java.util.List;
import java.util.UUID;

/**
 * One step of a workflow's path.
 *
 * @param handledBy the group that handles the step
 * @param deadline the legal deadline, in working days
 * @param reasons why the step may return or reject a request
 */
record Step(String id, StepType type, String name, String handledBy, Integer deadline, List<Reason> reasons) {

    Step {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    static Step request() {
        return new Step(newId(), StepType.REQUEST, "Pedido", null, null, List.of());
    }

    static Step review(String name, String handledBy, Integer deadline) {
        return new Step(newId(), StepType.REVIEW, name, handledBy, deadline, List.of());
    }

    static Step finalValidation() {
        return new Step(newId(), StepType.FINAL_VALIDATION, "Validação final", null, null, List.of());
    }

    Step withReasons(List<Reason> newReasons) {
        return new Step(id, type, name, handledBy, deadline, newReasons);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
