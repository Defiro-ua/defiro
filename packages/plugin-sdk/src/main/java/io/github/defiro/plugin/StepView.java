package io.github.defiro.plugin;

import java.util.List;

/**
 * What a step shows the person acting on it.
 *
 * @param fields the step's fields with their values; those filled in from the profile are locked
 * @param checks automatic checks, for example whether the certificate dates match the trip
 * @param notes the reviewers' notes on single fields, for example on a returned request
 */
public record StepView(List<ViewField> fields, List<CheckResult> checks, List<FieldNote> notes) {

    public StepView {
        fields = Require.copy(fields);
        checks = Require.copy(checks);
        notes = Require.copy(notes);
    }
}
