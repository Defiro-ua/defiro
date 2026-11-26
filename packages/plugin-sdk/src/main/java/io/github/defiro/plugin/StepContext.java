package io.github.defiro.plugin;

import io.github.defiro.spec.StepSpecification;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a plugin may know about the step it runs. It is read-only.
 *
 * @param procedure the procedure the step belongs to
 * @param step the step's configuration, from the procedure's own copy of the specification
 * @param data the answers of the current data version, by field id; empty before the first submission
 * @param requester who made the request
 * @param actingUser who is acting now
 * @param earlierOutcomes how each earlier step ended, by step id
 */
public record StepContext(
        UUID procedure,
        StepSpecification step,
        Map<String, Object> data,
        Person requester,
        Person actingUser,
        Map<String, String> earlierOutcomes) {

    public StepContext {
        Require.present(procedure, "A step runs inside a procedure");
        Require.present(step, "A context needs the step it runs");
        Require.present(requester, "A context needs the requester");
        Require.present(actingUser, "A context needs the acting user");
        data = Require.copy(data);
        earlierOutcomes = Require.copy(earlierOutcomes);
    }
}
