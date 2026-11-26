package io.github.defiro.plugin;

import java.util.List;
import java.util.Map;

/**
 * What a person did on a step: submitted data, or approved, returned or rejected it.
 *
 * @param action what they chose, for example "submit" or "return"
 * @param data the answers they submitted, by field id; empty for a review
 * @param reasons the reasons they chose, for a return or a rejection
 * @param message what they wrote to the requester, if anything
 * @param notes their notes on single fields
 */
public record Decision(
        String action, Map<String, Object> data, List<String> reasons, String message, List<FieldNote> notes) {

    public Decision {
        Require.text(action, "A decision needs its action");
        data = Require.copy(data);
        reasons = Require.copy(reasons);
        notes = Require.copy(notes);
    }

    /** The requester submits the answers of a request. */
    public static Decision submit(Map<String, Object> data) {
        return new Decision("submit", data, List.of(), null, List.of());
    }
}
