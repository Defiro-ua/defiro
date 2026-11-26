package io.github.defiro.plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The checks and copies the records of the contract share. */
final class Require {

    private Require() {}

    /** Fails with the message unless the value has some text. */
    static String text(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** Fails with the message when the value is missing. */
    static <T> T present(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** A copy that cannot change; empty when there is no list. */
    static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * A copy that cannot change; empty when there is no map. Unlike {@link Map#copyOf}, it keeps
     * entries whose value is null, as an answer left empty is.
     */
    static <K, V> Map<K, V> copy(Map<K, V> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
