package io.github.defiro.engine.internal;

import io.github.defiro.spec.StepSpecification;
import java.util.List;
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
        return jdbc.sql("""
                        select t.id, t.procedure_id, p.reference, t.step_id, p.draft, p.version,
                               r.data -> 'requester' ->> 'name' as requester_name,
                               r.data -> 'requester' ->> 'unit' as requester_unit
                        from engine.task t
                        join engine.procedure p on p.id = t.procedure_id
                        cross join lateral (
                            select data from engine.document d
                            where d.procedure_id = p.id
                            order by d.updated_at limit 1) r
                        where t.status = :status
                        order by p.created_at""")
                .param("status", OPEN)
                .query((row, n) -> new OpenTask(
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
