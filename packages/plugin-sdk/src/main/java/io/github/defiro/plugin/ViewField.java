package io.github.defiro.plugin;

/**
 * One field of a step's view.
 *
 * @param id the field's id in the specification
 * @param value what it holds now, if anything
 * @param locked true when the person cannot change it, as with fields filled in from the profile
 */
public record ViewField(String id, Object value, boolean locked) {

    public ViewField {
        Require.text(id, "A field of the view needs its id");
    }
}
