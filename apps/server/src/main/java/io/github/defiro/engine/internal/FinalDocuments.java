package io.github.defiro.engine.internal;

import io.github.defiro.identity.Person;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * UC23: the final document of a closed procedure, for its participants to open and download. It is
 * read as it was generated when the procedure was closed, never generated again.
 */
@Component
class FinalDocuments {

    private static final String CLOSED = "closed";

    private final Procedures procedures;
    private final GeneratedDocuments generatedDocuments;

    FinalDocuments(Procedures procedures, GeneratedDocuments generatedDocuments) {
        this.procedures = procedures;
        this.generatedDocuments = generatedDocuments;
    }

    /** The document, and its bytes as they were generated. */
    record Opened(GeneratedDocuments.Document document, byte[] content) {}

    /** The final document of the procedure, for its requester, once the procedure is closed and has one. */
    Optional<Opened> open(String reference, Person person) {
        return procedures
                .find(reference)
                .filter(procedure -> CLOSED.equals(procedure.status()))
                .filter(procedure -> procedure.requester().equals(person.id()))
                .flatMap(procedure -> generatedDocuments.of(procedure.id()))
                .map(document -> new Opened(document, generatedDocuments.content(document)));
    }
}
