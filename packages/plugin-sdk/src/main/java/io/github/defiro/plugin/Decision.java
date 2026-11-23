package io.github.defiro.plugin;

import java.util.List;

/**
 * What a person did on a step.
 *
 * @param action what they chose, for example "submitted" or "returned"
 * @param reasons the reasons they chose, for a return or a rejection; empty otherwise
 */
public record Decision(String action, List<String> reasons) {

    public Decision {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("A decision needs its action");
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
