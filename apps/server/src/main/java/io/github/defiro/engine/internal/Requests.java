package io.github.defiro.engine.internal;

import io.github.defiro.engine.internal.Files.StoredFile;
import io.github.defiro.engine.internal.Procedures.Procedure;
import io.github.defiro.identity.Person;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A request as the people who take part in it see it: its data, its files, its path and its
 * history. Taking part means having made it, being a candidate of its current step, or having
 * acted on it before. Nobody else may open it, nor its files.
 */
@Component
class Requests {

    private final Procedures procedures;
    private final SpecificationVersions versions;
    private final Tasks tasks;
    private final Candidates candidates;
    private final Files files;
    private final AuditLog auditLog;

    Requests(
            Procedures procedures,
            SpecificationVersions versions,
            Tasks tasks,
            Candidates candidates,
            Files files,
            AuditLog auditLog) {
        this.procedures = procedures;
        this.versions = versions;
        this.tasks = tasks;
        this.candidates = candidates;
        this.files = files;
        this.auditLog = auditLog;
    }

    record Step(String id, String name, String handledBy) {}

    record File(UUID id, String filename) {}

    record Request(
            String reference,
            String workflow,
            String status,
            String currentStep,
            List<Step> path,
            RequestData.Requester requester,
            List<RequestData.Answer> answers,
            List<File> files,
            List<History.Line> history) {}

    /** The request, or empty if there is none with this reference. */
    Optional<Request> open(String reference, Person person) {
        return procedures.find(reference).map(procedure -> {
            RequestData request = procedures.request(procedure.id());
            WorkflowSpecification specification = specification(procedure);
            List<AuditLog.Entry> entries = auditLog.of(procedure.id());
            mustTakePart(procedure, request, specification, entries, person);
            return new Request(
                    procedure.reference(),
                    specification.name(),
                    procedure.status(),
                    procedure.currentStep(),
                    specification.steps().stream()
                            .map(step -> new Step(step.id(), step.name(), step.handledBy()))
                            .toList(),
                    request.requester(),
                    request.answers(),
                    files.of(procedure.id()).stream()
                            .map(file -> new File(file.id(), file.filename()))
                            .toList(),
                    entries.stream().map(History::of).toList());
        });
    }

    /** A file of the request, or empty if the request has no such file. */
    Optional<StoredFile> file(String reference, UUID id, Person person) {
        return procedures.find(reference).flatMap(procedure -> {
            mustTakePart(
                    procedure,
                    procedures.request(procedure.id()),
                    specification(procedure),
                    auditLog.of(procedure.id()),
                    person);
            return files.find(id).filter(file -> file.procedure().equals(procedure.id()));
        });
    }

    byte[] content(StoredFile file) {
        return files.content(file);
    }

    private void mustTakePart(
            Procedure procedure,
            RequestData request,
            WorkflowSpecification specification,
            List<AuditLog.Entry> entries,
            Person person) {
        boolean requester = procedure.requester().equals(person.id());
        boolean actedOnIt = entries.stream().anyMatch(entry -> person.id().equals(entry.actor()));
        boolean candidate = tasks.openOf(procedure.id()).stream()
                .anyMatch(task -> candidates.canHandle(
                        step(specification, task.step()).handledBy(),
                        request.requester().unit(),
                        person));
        if (!requester && !actedOnIt && !candidate) {
            throw new NotAllowedException();
        }
    }

    private WorkflowSpecification specification(Procedure procedure) {
        return versions.find(procedure.draft(), procedure.version()).orElseThrow();
    }

    private static StepSpecification step(WorkflowSpecification specification, String id) {
        return Inbox.step(specification, id);
    }
}
