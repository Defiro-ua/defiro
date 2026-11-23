package io.github.defiro.plugin;

/**
 * Something wrong with one field of a step, shown next to that field.
 *
 * @param field the field's id
 * @param message what is wrong, as the person reads it
 */
public record FieldProblem(String field, String message) {

    public FieldProblem {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("A problem belongs to a field");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A problem needs a message saying what is wrong");
        }
    }
}
