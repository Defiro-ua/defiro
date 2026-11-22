package io.github.defiro.engine.internal;

import io.github.defiro.spec.StepSpecification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The tasks of the procedures: a step waiting for one of its candidates. Tasks are UC27 (Daniel);
 * UC17 opens them and decides them.
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
            String requesterUnit) {}

    /** A task as it is now: open, or done with the decision taken. */
    record Task(UUID id, UUID procedure, String step, String status, String decision) {

        boolean isOpen() {
            return OPEN.equals(status);
        }
    }

    Optional<Task> find(UUID id) {
        return jdbc.sql("select id, procedure_id, step_id, status, decision from engine.task where id = :id")
                .param("id", id)
                .query((row, n) -> new Task(
                        row.getObject("id", UUID.class),
                        row.getObject("procedure_id", UUID.class),
                        row.getString("step_id"),
                        row.getString("status"),
                        row.getString("decision")))
                .optional();
    }

    /**
     * Closes the task with the decision, if it is still open. Only one of two people deciding at
     * the same time gets true: the first to act wins.
     */
    boolean decide(UUID id, String decision) {
        return jdbc.sql("""
                                update engine.task set status = :done, decision = :decision, decided_at = now()
                                where id = :id and status = :open""")
                        .param("done", DONE)
                        .param("decision", decision)
                        .param("id", id)
                        .param("open", OPEN)
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
                        select t.id, t.procedure_id, p.reference, t.step_id, p.draft, p.version,
                               r.data -> 'requester' ->> 'name' as requester_name,
                               r.data -> 'requester' ->> 'unit' as requester_unit
                        from engine.task t
                        join engine.procedure p on p.id = t.procedure_id
                        cross join lateral (
                            select data from engine.document d
                            where d.procedure_id = p.id
                            order by d.updated_at limit 1) r
                        where t.status = :status %s
                        order by p.created_at""".formatted(andWhere)).param("status", OPEN);
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
                        row.getString("requester_unit")))
                .list();
    }
}
