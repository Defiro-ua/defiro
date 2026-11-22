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
    static final String TAKEN = "taken";
    static final String RELEASED = "released";
    private static final String NEXT_TASK = "nextTask";

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

    /** The task a candidate took, and the request it belongs to. */
    record Taken(UUID task, String reference) {}

    /**
     * A candidate of the step takes the task, so that nobody else reviews the request meanwhile.
     * Taking a task one already has changes nothing. If someone else has it, they are named.
     */
    @Transactional
    Optional<Taken> take(UUID id, Person person) {
        return tasks.find(id).map(task -> {
            Procedure procedure = procedures.find(task.procedure()).orElseThrow();
            StepSpecification step = mustBeCandidate(task, procedure, person);
            if (!task.isOpen()) {
                throw alreadyDecided(task);
            }
            if (tasks.take(task.id(), person.id())) {
                Map<String, Object> details = who(person);
                details.put("step", step.name());
                auditLog.append(person.id(), procedure.id(), TAKEN, "task", task.id(), details);
            } else if (!person.id()
                    .equals(tasks.find(task.id()).map(Task::takenBy).orElse(null))) {
                throw takenByOther(task.id());
            }
            return new Taken(task.id(), procedure.reference());
        });
    }

    /** Gives the task back to every candidate. Only who has it may. */
    @Transactional
    Optional<Taken> release(UUID id, Person person) {
        return tasks.find(id).map(task -> {
            if (!tasks.release(task.id(), person.id())) {
                throw new NotAllowedException();
            }
            Procedure procedure = procedures.find(task.procedure()).orElseThrow();
            Map<String, Object> details = who(person);
            details.put("step", step(procedure, task.step()).name());
            auditLog.append(person.id(), procedure.id(), RELEASED, "task", task.id(), details);
            return new Taken(task.id(), procedure.reference());
        });
    }

    /** Who has the task now, as «Rui Matos, Chefe de Divisão», if anyone. */
    Optional<String> reviewer(UUID task) {
        return auditLog.last(TAKEN, task)
                .map(taken ->
                        taken.details().get("name") + ", " + taken.details().get("jobTitle"));
    }

    /** Approves the task, or gives empty if there is no such task. */
    @Transactional
    Optional<Approved> approve(UUID id, Person person) {
        return tasks.find(id).map(task -> approve(task, person));
    }

    private Approved approve(Task task, Person person) {
        Procedure procedure = procedures.find(task.procedure()).orElseThrow();
        WorkflowSpecification specification =
                versions.find(procedure.draft(), procedure.version()).orElseThrow();
        StepSpecification step = mustBeCandidate(task, procedure, person);
        // Only who took the open task decides it; once decided, whoever comes late is told who did
        if (task.isOpen() && !person.id().equals(task.takenBy())) {
            throw new NotAllowedException();
        }
        if (!tasks.decide(task.id(), APPROVED, person.id())) {
            throw alreadyDecided(task);
        }
        String to = next(specification, step.id(), APPROVED);
        StepSpecification next = to == null ? null : Inbox.step(specification, to);
        UUID nextTask = null;
        if (next == null) {
            procedures.close(procedure.id());
        } else {
            procedures.moveTo(procedure.id(), next.id());
            nextTask = tasks.open(procedure.id(), next);
        }
        // The approval keeps the task it opened: undoing it withdraws that task, and no other.
        Map<String, Object> details = who(person);
        details.put("step", step.name());
        details.put(NEXT_TASK, nextTask == null ? null : nextTask.toString());
        auditLog.append(person.id(), procedure.id(), APPROVED, "task", task.id(), details);
        if (next == null) {
            return new Approved(task.id(), procedure.reference(), null, null, null);
        }
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
        Optional<AuditLog.Entry> approval =
                APPROVED.equals(task.decision()) ? auditLog.last(APPROVED, task.id()) : Optional.empty();
        if (approval.isEmpty() || !person.id().equals(approval.get().actor())) {
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
            // Only the task this approval opened, and only while still open: once someone acted on
            // it, or the request went back and on again, the approval stays.
            Object nextTask = approval.get().details().get(NEXT_TASK);
            if (nextTask == null || !tasks.withdraw(UUID.fromString(nextTask.toString()))) {
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

    // Who acted first: who decided the task, or, when its task was withdrawn meanwhile, who undid
    // the approval that had opened it.
    private StepSpecification mustBeCandidate(Task task, Procedure procedure, Person person) {
        StepSpecification step = step(procedure, task.step());
        String requesterUnit = procedures.request(procedure.id()).requester().unit();
        if (!candidates.canHandle(step.handledBy(), requesterUnit, person)) {
            throw new NotAllowedException();
        }
        return step;
    }

    private StepSpecification step(Procedure procedure, String step) {
        WorkflowSpecification specification =
                versions.find(procedure.draft(), procedure.version()).orElseThrow();
        return Inbox.step(specification, step);
    }

    private TakenByOtherException takenByOther(UUID task) {
        return new TakenByOtherException(reviewer(task).orElse(""));
    }

    private AlreadyDecidedException alreadyDecided(Task task) {
        Map<String, Object> first = auditLog.last(APPROVED, task.id())
                .or(() -> auditLog.latest(task.procedure(), UNDONE))
                .map(AuditLog.Entry::details)
                .orElse(Map.of());
        return new AlreadyDecidedException(String.valueOf(first.get("name")), String.valueOf(first.get("jobTitle")));
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
