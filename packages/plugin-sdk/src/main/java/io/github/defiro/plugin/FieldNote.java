package io.github.defiro.plugin;

/**
 * A reviewer's note on one field of a request (UC19).
 *
 * @param field the field's id
 * @param author the reviewer who wrote it
 */
public record FieldNote(String field, String text, Person author) {

    public FieldNote {
        Require.text(field, "A note belongs to a field");
        Require.text(text, "A note needs its text");
        Require.present(author, "A note needs who wrote it");
    }
}
