package io.github.defiro.engine.internal;

import io.github.defiro.engine.internal.Procedures.Procedure;
import io.github.defiro.engine.internal.Tasks.Task;
import io.github.defiro.identity.Person;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.Transition;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A reviewer's decision on a task: approving (UC17), or returning or rejecting (UC18). Only a
 * candidate of the step decides it, and the first of them to act wins. The request then goes where
 * the workflow's transitions say, and the history keeps who decided, with their name and job title
 * at the time.
 */
@Component
class Decisions {

    static final String APPROVED = "approved";
    // Also the status of the procedure they leave it in
    static final String RETURNED = "returned";
    static final String REJECTED = "rejected";
    static final String MOVED = "moved";
    static final String UNDONE = "undone";
    static final String TAKEN = "taken";
    static final String RELEASED = "released";
    static final String NEXT_TASK = "nextTask";
    static final String NOTES = "notes";
    static final String DECISION = "decision";
    static final String REASONS = "reasons";
    static final String MESSAGE = "message";
    static final int LONGEST_MESSAGE = 1000;

    private final Tasks tasks;
    private final Procedures procedures;
    private final SpecificationVersions versions;
    private final Candidates candidates;
    private final AuditLog auditLog;
    private final Notes notes;

    Decisions(
            Tasks tasks,
            Procedures procedures,
            SpecificationVersions versions,
            Candidates candidates,
            AuditLog auditLog,
            Notes notes) {
        this.tasks = tasks;
        this.procedures = procedures;
        this.versions = versions;
        this.candidates = candidates;
        this.auditLog = auditLog;
        this.notes = notes;
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
            // Notes never sent are discarded: whoever takes it next starts afresh (UC19)
            notes.discard(task.id());
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
        WorkflowSpecification specification = specification(procedure);
        StepSpecification step = mustBeCandidate(task, procedure, person);
        decide(task, APPROVED, person);
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
        // The reviewer's notes go on with the approval, to the next reviewer (UC19)
        details.put(NOTES, notes.send(task.id()));
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

    /** The task that was returned or rejected, and the request it belongs to. */
    record Ended(UUID task, String reference) {}

    /**
     * Returns the request to the requester, to be corrected (UC20), or gives empty if there is no such
     * task. The message may be left out if the reviewer left a note on a field: the notes go too.
     */
    @Transactional
    Optional<Ended> returnToRequester(UUID id, List<String> reasons, String message, Person person) {
        return tasks.find(id).map(task -> {
            Procedure procedure = procedures.find(task.procedure()).orElseThrow();
            StepSpecification step = mustBeCandidate(task, procedure, person);
            int sent = end(task, step, RETURNED, step.returnReasons(), reasons, message, person);
            if (isBlank(message) && sent == 0) {
                throw new InvalidDecisionException("A return with no notes needs a message");
            }
            procedures.returnTo(procedure.id(), next(specification(procedure), step.id(), RETURNED));
            return new Ended(task.id(), procedure.reference());
        });
    }

    /**
     * Ends the request as rejected, or gives empty if there is no such task. It always needs a message:
     * the notes do not go to the requester with a rejection (Pedro, 2026-11-23).
     */
    @Transactional
    Optional<Ended> reject(UUID id, List<String> reasons, String message, Person person) {
        return tasks.find(id).map(task -> {
            Procedure procedure = procedures.find(task.procedure()).orElseThrow();
            StepSpecification step = mustBeCandidate(task, procedure, person);
            end(task, step, REJECTED, step.rejectReasons(), reasons, message, person);
            if (isBlank(message)) {
                throw new InvalidDecisionException("A rejection needs a message");
            }
            procedures.reject(procedure.id());
            return new Ended(task.id(), procedure.reference());
        });
    }

    // Decides the task for the reasons chosen, and gives how many notes went with it. Whatever is
    // refused here or after it rolls the decision back, so nothing is checked first and written after.
    private int end(
            Task task,
            StepSpecification step,
            String decision,
            List<String> allowed,
            List<String> reasons,
            String message,
            Person person) {
        decide(task, decision, person);
        if (reasons == null || reasons.isEmpty() || !allowed.containsAll(reasons)) {
            throw new InvalidDecisionException("The reasons are not the step's, or there are none");
        }
        if (message != null && message.strip().length() > LONGEST_MESSAGE) {
            throw new InvalidDecisionException("The message has more than " + LONGEST_MESSAGE + " characters");
        }
        int sent = notes.send(task.id());
        Map<String, Object> details = who(person);
        details.put("step", step.name());
        // Once each, in the order the workflow has them
        details.put(REASONS, allowed.stream().filter(reasons::contains).toList());
        details.put(MESSAGE, isBlank(message) ? null : message.strip());
        details.put(NOTES, sent);
        auditLog.append(person.id(), task.procedure(), decision, "task", task.id(), details);
        return sent;
    }

    /** The reasons an entry of the audit log keeps. */
    static List<String> reasonsOf(Map<String, Object> details) {
        return details.get(REASONS) instanceof List<?> reasons
                ? reasons.stream().map(String::valueOf).toList()
                : List.of();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    // Only who took the open task decides it; once decided, whoever comes late is told who did
    private void decide(Task task, String decision, Person person) {
        if (task.isOpen() && !person.id().equals(task.takenBy())) {
            throw new NotAllowedException();
        }
        if (!tasks.decide(task.id(), decision, person.id())) {
            throw alreadyDecided(task);
        }
    }

    /** Where the request went back to: the step that was decided, now open again, and how it had been. */
    record Undone(UUID task, String reference, String step, String decision) {}

    /**
     * Undoes a decision, while nobody has acted after it (Pedro, 2026-11-22): the decided task opens
     * again, and the request goes back to it. An approval's next task goes away; a return can be undone
     * until the requester resubmits. Only the reviewer who decided may undo, and the history keeps both
     * the decision and its undoing.
     */
    @Transactional
    Optional<Undone> undo(UUID id, Person person) {
        return tasks.find(id).map(task -> undo(task, person));
    }

    private Undone undo(Task task, Person person) {
        String decision = task.decision();
        AuditLog.Entry decided = Optional.ofNullable(decision)
                .flatMap(taken -> auditLog.last(taken, task.id()))
                .filter(entry -> person.id().equals(entry.actor()))
                .orElseThrow(NotAllowedException::new);
        Procedure procedure = procedures.find(task.procedure()).orElseThrow();
        WorkflowSpecification specification = specification(procedure);
        StepSpecification step = Inbox.step(specification, task.step());
        if (APPROVED.equals(decision)) {
            withdrawNext(specification, step, procedure, decided);
        } else if (!procedures.takeBack(task.id(), decision)) {
            // Returned: the requester resubmitted it, at the request's step
            String after = next(specification, step.id(), decision);
            throw new NoLongerUndoableException(
                    after == null
                            ? step.name()
                            : Inbox.step(specification, after).name());
        }
        tasks.reopen(task.id());
        // The notes are the reviewer's draft again, to change before deciding again (UC19)
        notes.backToDraft(task.id());
        procedures.moveTo(procedure.id(), step.id());
        Map<String, Object> details = who(person);
        details.put("step", step.name());
        details.put(DECISION, decision);
        auditLog.append(person.id(), procedure.id(), UNDONE, "task", task.id(), details);
        return new Undone(task.id(), procedure.reference(), step.name(), decision);
    }

    private void withdrawNext(
            WorkflowSpecification specification, StepSpecification step, Procedure procedure, AuditLog.Entry approval) {
        String to = next(specification, step.id(), APPROVED);
        if (to == null) {
            procedures.reopen(procedure.id());
            return;
        }
        // Only the task this approval opened, and only while still open: once someone acted on it, or
        // the request went back and on again, the approval stays.
        Object nextTask = approval.details().get(NEXT_TASK);
        if (nextTask == null || !tasks.withdraw(UUID.fromString(nextTask.toString()))) {
            throw new NoLongerUndoableException(Inbox.step(specification, to).name());
        }
    }

    private StepSpecification mustBeCandidate(Task task, Procedure procedure, Person person) {
        StepSpecification step = step(procedure, task.step());
        String requesterUnit = procedures.request(procedure.id()).requester().unit();
        if (!candidates.canHandle(step.handledBy(), requesterUnit, person)) {
            throw new NotAllowedException();
        }
        return step;
    }

    private StepSpecification step(Procedure procedure, String step) {
        return Inbox.step(specification(procedure), step);
    }

    private WorkflowSpecification specification(Procedure procedure) {
        return versions.find(procedure.draft(), procedure.version()).orElseThrow();
    }

    private TakenByOtherException takenByOther(UUID task) {
        return new TakenByOtherException(reviewer(task).orElse(""));
    }

    // Who acted first: who decided the task, or, when its task was withdrawn meanwhile, who undid
    // the approval that had opened it.
    private AlreadyDecidedException alreadyDecided(Task task) {
        // Read again: the task may have been open when this request started. An undone decision is no
        // longer the task's.
        Map<String, Object> first = tasks.find(task.id())
                .map(Task::decision)
                .flatMap(decision -> auditLog.last(decision, task.id()))
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
