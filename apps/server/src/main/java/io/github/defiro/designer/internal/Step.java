package io.github.defiro.designer.internal;

import java.util.UUID;

/**
 * One step of a workflow's path.
 *
 * @param handledBy the group that handles the step
 * @param deadline the legal deadline, in working days
 */
record Step(String id, StepType type, String name, String handledBy, Integer deadline) {

    static Step request() {
        return new Step(newId(), StepType.REQUEST, "Pedido", null, null);
    }

    static Step review(String name, String handledBy, Integer deadline) {
        return new Step(newId(), StepType.REVIEW, name, handledBy, deadline);
    }

    static Step finalValidation() {
        return new Step(newId(), StepType.FINAL_VALIDATION, "Validação final", null, null);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
