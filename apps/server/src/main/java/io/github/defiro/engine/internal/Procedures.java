package io.github.defiro.engine.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The procedures and the request each one started with. Creating them is UC13 (Dinis); until then
 * only the acceptance tests do.
 */
@Repository
class Procedures {

    /**
     * The document of the request: the one of the first step of the procedure's path. A procedure
     * has other documents, of other steps, and the request's changes when it is corrected. The %s
     * is the procedure's id; the aliases are the query's own, so it can be a subquery.
     */
    static final String REQUEST_DOCUMENT = """
            select request_document.data from engine.document request_document
            join engine.procedure request_procedure on request_procedure.id = request_document.procedure_id
            join engine.specification_version request_version
                on request_version.draft = request_procedure.draft and request_version.version = request_procedure.version
            where request_document.procedure_id = %s
              and request_document.step_id = request_version.specification -> 'steps' -> 0 ->> 'id'""";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    Procedures(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** A procedure, on its own version of the workflow, waiting at its current step. */
    record Procedure(
            UUID id, String reference, UUID draft, int version, String requester, String currentStep, String status) {}

    Optional<Procedure> find(String reference) {
        return jdbc.sql("""
                        select id, reference, draft, version, requester_id, current_step_id, status
                        from engine.procedure where reference = :reference""")
                .param("reference", reference)
                .query((row, n) -> new Procedure(
                        row.getObject("id", UUID.class),
                        row.getString("reference"),
                        row.getObject("draft", UUID.class),
                        row.getInt("version"),
                        row.getString("requester_id"),
                        row.getString("current_step_id"),
                        row.getString("status")))
                .optional();
    }

    /**
     * A request of this requester, as «Os meus pedidos» lists it. The names are the procedure's own
     * version's, and the current step is null once it is closed.
     */
    record Mine(
            String reference,
            String workflow,
            String status,
            String currentStep,
            OffsetDateTime closedAt,
            boolean hasDocument) {}

    /** The requester's procedures, the newest first: one query, reading the names off the specification. */
    List<Mine> of(String requester) {
        return jdbc.sql("""
                        select p.reference,
                               v.specification ->> 'name' as workflow,
                               p.status,
                               (select s ->> 'name' from jsonb_array_elements(v.specification -> 'steps') s
                                where s ->> 'id' = p.current_step_id) as current_step,
                               p.closed_at,
                               (p.status = 'closed' and g.procedure_id is not null) as has_document
                        from engine.procedure p
                        join engine.specification_version v on v.draft = p.draft and v.version = p.version
                        left join engine.generated_document g on g.procedure_id = p.id
                        where p.requester_id = :requester
                        order by p.created_at desc, p.id""")
                .param("requester", requester)
                .query((row, n) -> new Mine(
                        row.getString("reference"),
                        row.getString("workflow"),
                        row.getString("status"),
                        row.getString("current_step"),
                        row.getObject("closed_at", OffsetDateTime.class),
                        row.getBoolean("has_document")))
                .list();
    }

    Optional<Procedure> find(UUID id) {
        return jdbc.sql("select reference from engine.procedure where id = :id")
                .param("id", id)
                .query(String.class)
                .optional()
                .flatMap(this::find);
    }

    /** The procedure waits at this step now. */
    void moveTo(UUID procedure, String step) {
        jdbc.sql("update engine.procedure set current_step_id = :step where id = :id")
                .param("step", step)
                .param("id", procedure)
                .update();
    }

    /** The procedure went through its whole path. */
    void close(UUID procedure) {
        jdbc.sql("""
                        update engine.procedure set current_step_id = null, status = 'closed', closed_at = now()
                        where id = :id""").param("id", procedure).update();
    }

    /** The procedure goes on again, after the approval of its last step was undone. */
    void reopen(UUID procedure) {
        jdbc.sql("update engine.procedure set status = 'in_progress', closed_at = null where id = :id")
                .param("id", procedure)
                .update();
    }

    /** The request went back to the requester, at the request's step, to be corrected (UC20). */
    void returnTo(UUID procedure, String step) {
        jdbc.sql("update engine.procedure set current_step_id = :step, status = :returned where id = :id")
                .param("step", step)
                .param("returned", Decisions.RETURNED)
                .param("id", procedure)
                .update();
    }

    /** The procedure ended at its current step, rejected. */
    void reject(UUID procedure) {
        jdbc.sql("update engine.procedure set status = :rejected, closed_at = now() where id = :id")
                .param("rejected", Decisions.REJECTED)
                .param("id", procedure)
                .update();
    }

    /**
     * The procedure goes on again, after the return or rejection of this task was undone. Gives false,
     * and changes nothing, if it is no longer as that decision left it: the requester resubmitted it,
     * even if it was decided again since. As in Tasks, one conditional statement.
     */
    boolean takeBack(UUID task, String decision) {
        return jdbc.sql("""
                                update engine.procedure p set status = 'in_progress', closed_at = null
                                from engine.task t
                                where t.id = :task and t.decision = :decision
                                  and p.id = t.procedure_id and p.status = :decision
                                  and not exists (select 1 from engine.task later
                                                  where later.procedure_id = p.id and later.decided_at > t.decided_at)""").param("task", task).param("decision", decision).update() == 1;
    }

    /** The request: the document of the procedure's first step. */
    RequestData request(UUID procedure) {
        String data = jdbc.sql(REQUEST_DOCUMENT.formatted(":procedure"))
                .param("procedure", procedure)
                .query(String.class)
                .single();
        return json.readValue(data, RequestData.class);
    }
}
