package io.github.defiro.spec;

import java.util.List;
import java.util.Set;

/**
 * One question of the request form (UC04). Name, organizational unit and manager are not
 * questions: the Engine always fills them in from the requester's profile.
 *
 * @param type one of {@link #TYPES}
 * @param options the answers to choose from, for a {@link #SINGLE_CHOICE}; empty otherwise
 */
public record FieldSpecification(String id, String label, String type, boolean required, List<String> options) {

    public static final String SINGLE_CHOICE = "single-choice";

    /** The answer types, in the order the designer shows their cards */
    public static final Set<String> TYPES =
            Set.of("short-text", "long-text", "number", "date", "date-time", "yes-no", SINGLE_CHOICE, "files");

    public FieldSpecification {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
