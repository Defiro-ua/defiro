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
    private final AuditLog auditLog;

    FinalDocuments(Procedures procedures, GeneratedDocuments generatedDocuments, AuditLog auditLog) {
        this.auditLog = auditLog;
        this.procedures = procedures;
        this.generatedDocuments = generatedDocuments;
    }

    /** The document, and its bytes as they were generated. */
    record Opened(GeneratedDocuments.Document document, byte[] content) {}

    /**
     * The final document of the procedure, once it is closed and has one. Only its requester and
     * whoever closed it may open it, which is stricter than opening the request (UC17): the others
     * are refused, even if they approved an earlier step. They are refused before anything is said
     * about the procedure, so that they can't learn whether it is closed.
     */
    Optional<Opened> open(String reference, Person person) {
        return procedures.find(reference).flatMap(procedure -> {
            if (!procedure.requester().equals(person.id()) && !closedBy(procedure, person)) {
                throw new NotAllowedException();
            }
            if (!CLOSED.equals(procedure.status())) {
                return Optional.empty();
            }
            return generatedDocuments
                    .of(procedure.id())
                    .map(document -> new Opened(document, generatedDocuments.content(document)));
        });
    }

    // The closing is the newest approval of a procedure that is closed now: an approval that was
    // undone, or an earlier one, closed nothing.
    private boolean closedBy(Procedures.Procedure procedure, Person person) {
        return CLOSED.equals(procedure.status())
                && auditLog.latest(procedure.id(), Decisions.APPROVED)
                        .map(entry -> person.id().equals(entry.actor()))
                        .orElse(false);
    }
}
