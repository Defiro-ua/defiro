package io.github.defiro.plugin;

/**
 * Something wrong with one field of a step, shown next to that field.
 *
 * @param field the field's id
 * @param message what is wrong, as the person reads it
 */
public record FieldProblem(String field, String message) {

    public FieldProblem {
        Require.text(field, "A problem belongs to a field");
        Require.text(message, "A problem needs a message saying what is wrong");
    }
}
