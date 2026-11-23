package io.github.defiro.plugin;

import io.github.defiro.spec.StepSpecification;
import java.util.UUID;

/**
 * The step a plugin runs, in one procedure.
 *
 * @param procedure the procedure the step belongs to
 * @param step the step as the procedure's version of the workflow specifies it
 */
public record StepContext(UUID procedure, StepSpecification step) {

    public StepContext {
        if (procedure == null) {
            throw new IllegalArgumentException("A step runs inside a procedure");
        }
        if (step == null) {
            throw new IllegalArgumentException("A context needs the step it runs");
        }
    }
}
