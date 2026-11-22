package io.github.defiro.engine.internal;

import io.github.defiro.engine.internal.Files.StoredFile;
import io.github.defiro.engine.internal.Procedures.Procedure;
import io.github.defiro.identity.Person;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

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
    private final JsonMapper json;
    private final Decisions decisions;
    private final Notes notes;

    Requests(
            Procedures procedures,
            SpecificationVersions versions,
            Tasks tasks,
            Candidates candidates,
            Files files,
            AuditLog auditLog,
            JsonMapper json,
            Decisions decisions,
            Notes notes) {
        this.procedures = procedures;
        this.versions = versions;
        this.tasks = tasks;
        this.candidates = candidates;
        this.files = files;
        this.auditLog = auditLog;
        this.json = json;
        this.decisions = decisions;
        this.notes = notes;
    }

    record Step(String id, String name, String handledBy) {}

    /**
     * An answer of the current version. On a resubmitted request, a changed answer is marked, with
     * the answer of the version before.
     */
    record Answer(String field, String label, String value, boolean changed, String previous) {}

    /** A file, with the version of the request it was attached in. */
    record File(UUID id, String filename, int version) {}

    /**
     * The request as this person sees it. The task is the one they may decide now, as a candidate
     * of the current step, or null.
     */
    record Request(
            String reference,
            String workflow,
            String status,
            String currentStep,
            List<Step> path,
            int version,
            RequestData.Requester requester,
            List<Answer> answers,
            List<File> files,
            List<History.Line> history,
            UUID task,
            String reviewer,
            List<Notes.Note> notes,
            List<Notes.NoteFrom> notesFrom) {}

    /** The request, or empty if there is none with this reference. */
    Optional<Request> open(String reference, Person person) {
        return procedures.find(reference).map(procedure -> {
            RequestData request = procedures.request(procedure.id());
            WorkflowSpecification specification = specification(procedure);
            List<AuditLog.Entry> entries = auditLog.of(procedure.id());
            Optional<Tasks.OpenTask> candidateOf = mustTakePart(procedure, request, specification, entries, person);
            // The person decides the task only once they took it («Rever» in the inbox)
            UUID task = candidateOf
                    .filter(open -> person.id().equals(open.takenBy()))
                    .map(Tasks.OpenTask::id)
                    .orElse(null);
            String reviewer = tasks.openOf(procedure.id()).stream()
                    .filter(open -> open.takenBy() != null)
                    .findFirst()
                    .flatMap(open -> decisions.reviewer(open.id()))
                    .orElse(null);
            List<Resubmission> resubmissions = entries.stream()
                    .filter(entry -> Resubmission.ACTION.equals(entry.action()))
                    .map(entry -> json.convertValue(entry.details(), Resubmission.class))
                    .toList();
            return new Request(
                    procedure.reference(),
                    specification.name(),
                    procedure.status(),
                    procedure.currentStep(),
                    specification.steps().stream()
                            .map(step -> new Step(step.id(), step.name(), step.handledBy()))
                            .toList(),
                    resubmissions.isEmpty() ? 1 : resubmissions.getLast().version(),
                    request.requester(),
                    answers(request, resubmissions),
                    files(procedure, resubmissions),
                    entries.stream().map(History::of).toList(),
                    task,
                    reviewer,
                    // Their own notes, while they have the task; nobody else sees them (UC19)
                    task == null ? List.of() : notes.draftOf(task),
                    candidateOf.map(open -> notesFrom(entries, open.id())).orElse(List.of()));
        });
    }

    // The notes sent with the approval that opened this task: the candidates of its step see them
    private List<Notes.NoteFrom> notesFrom(List<AuditLog.Entry> entries, UUID task) {
        return entries.stream()
                .filter(entry -> Decisions.APPROVED.equals(entry.action()))
                .filter(entry -> task.toString().equals(entry.details().get(Decisions.NEXT_TASK)))
                .reduce((first, second) -> second)
                .map(approval -> notes.sentWith(UUID.fromString(approval.entity())))
                .orElse(List.of());
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

    // Compared with the version before the current one, if the request was resubmitted.
    private static List<Answer> answers(RequestData request, List<Resubmission> resubmissions) {
        Map<String, String> before = resubmissions.isEmpty()
                ? null
                : resubmissions.getLast().previousAnswers().stream()
                        .collect(HashMap::new, (map, answer) -> map.put(answer.field(), answer.value()), Map::putAll);
        return request.answers().stream()
                .map(answer -> {
                    boolean changed = before != null && !Objects.equals(before.get(answer.field()), answer.value());
                    return new Answer(
                            answer.field(),
                            answer.label(),
                            answer.value(),
                            changed,
                            changed ? before.get(answer.field()) : null);
                })
                .toList();
    }

    // A file is of version 1, unless a resubmission added it.
    private List<File> files(Procedure procedure, List<Resubmission> resubmissions) {
        Map<UUID, Integer> addedIn = new HashMap<>();
        resubmissions.forEach(
                resubmission -> resubmission.newFiles().forEach(file -> addedIn.put(file, resubmission.version())));
        return files.of(procedure.id()).stream()
                .map(file -> new File(file.id(), file.filename(), addedIn.getOrDefault(file.id(), 1)))
                .toList();
    }

    byte[] content(StoredFile file) {
        return files.content(file);
    }

    /**
     * The open task of the request this person is a candidate of, if any. Anyone else who takes no part
     * in the request may not open it.
     */
    private Optional<Tasks.OpenTask> mustTakePart(
            Procedure procedure,
            RequestData request,
            WorkflowSpecification specification,
            List<AuditLog.Entry> entries,
            Person person) {
        Optional<Tasks.OpenTask> task = tasks.openOf(procedure.id()).stream()
                .filter(open -> candidates.canHandle(
                        step(specification, open.step()).handledBy(),
                        request.requester().unit(),
                        person))
                .findFirst();
        boolean requester = procedure.requester().equals(person.id());
        boolean actedOnIt = entries.stream().anyMatch(entry -> person.id().equals(entry.actor()));
        if (!requester && !actedOnIt && task.isEmpty()) {
            throw new NotAllowedException();
        }
        return task;
    }

    private WorkflowSpecification specification(Procedure procedure) {
        return versions.find(procedure.draft(), procedure.version()).orElseThrow();
    }

    private static StepSpecification step(WorkflowSpecification specification, String id) {
        return Inbox.step(specification, id);
    }
}
