package io.github.defiro.engine.internal;

import java.util.List;

/**
 * What a document of the request keeps: the requester as Entra ID gave them when the request was
 * made (the «Requerente» section), and the answers of the form.
 *
 * <p>open question (Dinis, UC13; Candeias, UC04): the answers are kept as {field, label, value},
 * with the prototype's field keys, until the Designer defines the form's fields.
 */
public record RequestData(Requester requester, List<Answer> answers) {

    public RequestData {
        answers = List.copyOf(answers);
    }

    public record Requester(String id, String name, String jobTitle, String unit, String manager) {}

    public record Answer(String field, String label, String value) {}
}
