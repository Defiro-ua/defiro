package io.github.defiro.engine.internal;

import io.github.defiro.engine.internal.Procedures.Procedure;
import io.github.defiro.engine.internal.Tasks.Task;
import io.github.defiro.identity.Person;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.Transition;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A reviewer's decision on a task (UC17). Only a candidate of the step decides it, and the first of
 * them to act wins. The request then goes where the workflow's transitions say, and the history
 * keeps who decided, with their name and job title at the time.
 */
@Component
class Decisions {

    static final String APPROVED = "approved";
    static final String MOVED = "moved";
    static final String UNDONE = "undone";

    private final Tasks tasks;
    private final Procedures procedures;
    private final SpecificationVersions versions;
    private final Candidates candidates;
    private final AuditLog auditLog;

    Decisions(
            Tasks tasks,
            Procedures procedures,
            SpecificationVersions versions,
            Candidates candidates,
            AuditLog auditLog) {
        this.tasks = tasks;
        this.procedures = procedures;
        this.versions = versions;
        this.candidates = candidates;
        this.auditLog = auditLog;
    }

    /**
     * Where the approved request went: the next step and who handles it, with its task; or no step,
     * when the approved step was the last.
     */
    record Approved(UUID task, String reference, String nextStep, String handledBy, UUID nextTask) {}

    /** Approves the task, or gives empty if there is no such task. */
    @Transactional
    Optional<Approved> approve(UUID id, Person person) {
        return tasks.find(id).map(task -> approve(task, person));
    }

    private Approved approve(Task task, Person person) {
        Procedure procedure = procedures.find(task.procedure()).orElseThrow();
        WorkflowSpecification specification =
                versions.find(procedure.draft(), procedure.version()).orElseThrow();
        StepSpecification step = Inbox.step(specification, task.step());
        String requesterUnit = procedures.request(procedure.id()).requester().unit();
        if (!candidates.canHandle(step.handledBy(), requesterUnit, person)) {
            throw new NotAllowedException();
        }
        if (!tasks.decide(task.id(), APPROVED)) {
            throw alreadyDecided(task);
        }
        Map<String, Object> details = who(person);
        details.put("step", step.name());
        auditLog.append(person.id(), procedure.id(), APPROVED, "task", task.id(), details);

        String to = next(specification, step.id(), APPROVED);
        if (to == null) {
            procedures.close(procedure.id());
            return new Approved(task.id(), procedure.reference(), null, null, null);
        }
        StepSpecification next = Inbox.step(specification, to);
        procedures.moveTo(procedure.id(), next.id());
        UUID nextTask = tasks.open(procedure.id(), next);
        Map<String, Object> moved = new LinkedHashMap<>();
        moved.put("step", next.name());
        moved.put("handledBy", next.handledBy());
        auditLog.append(null, procedure.id(), MOVED, "task", nextTask, moved);
        return new Approved(task.id(), procedure.reference(), next.name(), next.handledBy(), nextTask);
    }

    /** Where the request went back to: the step that was approved, now open again. */
    record Undone(UUID task, String reference, String step) {}

    /**
     * Undoes an approval, while nobody has acted on the next step (Pedro, 2026-11-22): that step's
     * task goes away, the approved one opens again, and the request goes back. Only the reviewer who
     * approved may undo, and the history keeps both the approval and its undoing.
     */
    @Transactional
    Optional<Undone> undo(UUID id, Person person) {
        return tasks.find(id).map(task -> undo(task, person));
    }

    private Undone undo(Task task, Person person) {
        boolean approvedByThisPerson = APPROVED.equals(task.decision())
                && auditLog.last(APPROVED, task.id())
                        .map(approval -> person.id().equals(approval.actor()))
                        .orElse(false);
        if (!approvedByThisPerson) {
            throw new NotAllowedException();
        }
        Procedure procedure = procedures.find(task.procedure()).orElseThrow();
        WorkflowSpecification specification =
                versions.find(procedure.draft(), procedure.version()).orElseThrow();
        StepSpecification step = Inbox.step(specification, task.step());
        String to = next(specification, step.id(), APPROVED);
        if (to == null) {
            procedures.reopen(procedure.id());
        } else {
            StepSpecification next = Inbox.step(specification, to);
            Optional<Tasks.OpenTask> nextTask = tasks.openOf(procedure.id()).stream()
                    .filter(open -> open.step().equals(next.id()))
                    .findFirst();
            // Withdrawn only if still open: nobody may act on it in the meantime.
            if (nextTask.isEmpty() || !tasks.withdraw(nextTask.get().id())) {
                throw new NoLongerUndoableException(next.name());
            }
        }
        tasks.reopen(task.id());
        procedures.moveTo(procedure.id(), step.id());
        Map<String, Object> details = who(person);
        details.put("step", step.name());
        auditLog.append(person.id(), procedure.id(), UNDONE, "task", task.id(), details);
        return new Undone(task.id(), procedure.reference(), step.name());
    }

    private AlreadyDecidedException alreadyDecided(Task task) {
        Map<String, Object> decided =
                auditLog.last(APPROVED, task.id()).map(AuditLog.Entry::details).orElse(Map.of());
        return new AlreadyDecidedException(
                String.valueOf(decided.get("name")), String.valueOf(decided.get("jobTitle")));
    }

    private static String next(WorkflowSpecification specification, String step, String outcome) {
        return specification.transitions().stream()
                .filter(transition ->
                        transition.from().equals(step) && transition.outcome().equals(outcome))
                .findFirst()
                // After the last step there is none: the transition goes to null.
                .map(Transition::to)
                .orElse(null);
    }

    // The name and job title of the person at the time, as the history shows them.
    private static Map<String, Object> who(Person person) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", person.name());
        details.put("jobTitle", person.jobTitle());
        return details;
    }
}
