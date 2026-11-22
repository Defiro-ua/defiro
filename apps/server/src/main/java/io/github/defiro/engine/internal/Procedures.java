package io.github.defiro.engine.internal;

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

    /** The request: the document of the procedure's first step. */
    RequestData request(UUID procedure) {
        String data = jdbc.sql(REQUEST_DOCUMENT.formatted(":procedure"))
                .param("procedure", procedure)
                .query(String.class)
                .single();
        return json.readValue(data, RequestData.class);
    }
}
