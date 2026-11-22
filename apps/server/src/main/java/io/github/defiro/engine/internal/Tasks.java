package io.github.defiro.engine.internal;

import io.github.defiro.spec.StepSpecification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The tasks of the procedures: a step waiting for one of its candidates. Tasks are UC27 (Daniel);
 * UC17 opens them, lets a candidate take one, and decides them.
 *
 * <p>Two people may act on the same task at the same time. Every change is one conditional
 * statement (taking, releasing, deciding, withdrawing), so PostgreSQL locks the row only for that
 * statement: the first one changes it, and the other finds the condition no longer true and
 * changes nothing. Nothing is read first and written after.
 */
@Repository
class Tasks {

    static final String OPEN = "open";
    static final String DONE = "done";

    private final JdbcClient jdbc;
    private final Candidates candidates;

    Tasks(JdbcClient jdbc, Candidates candidates) {
        this.jdbc = jdbc;
        this.candidates = candidates;
    }

    /**
     * An open task, with what deciding who handles it needs: the procedure's version, to find the
     * step, and the requester's unit, as the request keeps it.
     */
    record OpenTask(
            UUID id,
            UUID procedure,
            String reference,
            String step,
            UUID draft,
            int version,
            String requesterName,
            String requesterUnit,
            String takenBy) {}

    /** A task as it is now: open, taken by someone or not, or done with the decision taken. */
    record Task(UUID id, UUID procedure, String step, String status, String decision, String takenBy) {

        boolean isOpen() {
            return OPEN.equals(status);
        }
    }

    Optional<Task> find(UUID id) {
        return jdbc.sql("""
                        select id, procedure_id, step_id, status, decision, assigned_user_id
                        from engine.task where id = :id""")
                .param("id", id)
                .query((row, n) -> new Task(
                        row.getObject("id", UUID.class),
                        row.getObject("procedure_id", UUID.class),
                        row.getString("step_id"),
                        row.getString("status"),
                        row.getString("decision"),
                        row.getString("assigned_user_id")))
                .optional();
    }

    /**
     * The person takes the open task, if nobody has: the task is theirs until they decide it or
     * release it (the diagram's assigned_user_id). Of two people taking it at once, only one gets
     * true.
     */
    boolean take(UUID id, String person) {
        return jdbc.sql("""
                                update engine.task set assigned_user_id = :person
                                where id = :id and status = :open and assigned_user_id is null""")
                        .param("person", person)
                        .param("id", id)
                        .param("open", OPEN)
                        .update()
                == 1;
    }

    /** Gives the open task back to every candidate, if this person has it. */
    boolean release(UUID id, String person) {
        return jdbc.sql("""
                                update engine.task set assigned_user_id = null
                                where id = :id and status = :open and assigned_user_id = :person""")
                        .param("id", id)
                        .param("open", OPEN)
                        .param("person", person)
                        .update()
                == 1;
    }

    /**
     * Closes the task with the decision, if it is still open and this person has it. Of two
     * decisions at the same time, only one gets true: the first to act wins. The task stays taken
     * by whoever decided it, so undoing gives it back to them.
     */
    boolean decide(UUID id, String decision, String person) {
        return jdbc.sql("""
                                update engine.task set status = :done, decision = :decision, decided_at = now()
                                where id = :id and status = :open and assigned_user_id = :person""")
                        .param("done", DONE)
                        .param("decision", decision)
                        .param("id", id)
                        .param("open", OPEN)
                        .param("person", person)
                        .update()
                == 1;
    }

    /**
     * Removes a task nobody has acted on, when the decision that opened it is undone. It is only
     * removed while still open; the history keeps what happened.
     */
    boolean withdraw(UUID id) {
        return jdbc.sql("delete from engine.task where id = :id and status = :open")
                        .param("id", id)
                        .param("open", OPEN)
                        .update()
                == 1;
    }

    /** Opens a decided task again, with no decision, when its decision is undone. */
    void reopen(UUID id) {
        jdbc.sql("""
                        update engine.task set status = :open, decision = null, decided_at = null
                        where id = :id""").param("open", OPEN).param("id", id).update();
    }

    /** Opens the task of a step for its candidates. */
    UUID open(UUID procedure, StepSpecification step) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into engine.task (id, procedure_id, step_id, group_id, status)
                        values (:id, :procedure, :step, :group, :status)""")
                .param("id", id)
                .param("procedure", procedure)
                .param("step", step.id())
                .param("group", candidates.groupOf(step.handledBy()))
                .param("status", OPEN)
                .update();
        return id;
    }

    /** Every open task, the oldest request first. */
    List<OpenTask> open() {
        return open("", null);
    }

    /** The open tasks of a procedure. */
    List<OpenTask> openOf(UUID procedure) {
        return open("and t.procedure_id = :procedure", procedure);
    }

    private List<OpenTask> open(String andWhere, UUID procedure) {
        var query = jdbc.sql("""
                        select t.id, t.procedure_id, p.reference, t.step_id, p.draft, p.version, t.assigned_user_id,
                               r.data -> 'requester' ->> 'name' as requester_name,
                               r.data -> 'requester' ->> 'unit' as requester_unit
                        from engine.task t
                        join engine.procedure p on p.id = t.procedure_id
                        cross join lateral (%s) r
                        where t.status = :status %s
                        order by p.created_at""".formatted(Procedures.REQUEST_DOCUMENT.formatted("p.id"), andWhere))
                .param("status", OPEN);
        if (procedure != null) {
            query = query.param("procedure", procedure);
        }
        return query.query((row, n) -> new OpenTask(
                        row.getObject("id", UUID.class),
                        row.getObject("procedure_id", UUID.class),
                        row.getString("reference"),
                        row.getString("step_id"),
                        row.getObject("draft", UUID.class),
                        row.getInt("version"),
                        row.getString("requester_name"),
                        row.getString("requester_unit"),
                        row.getString("assigned_user_id")))
                .list();
    }
}
