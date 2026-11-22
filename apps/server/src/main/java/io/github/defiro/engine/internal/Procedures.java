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

    /** The request: the document of the procedure's first step. */
    RequestData request(UUID procedure) {
        String data =
                jdbc.sql("""
                        select data from engine.document where procedure_id = :procedure
                        order by updated_at limit 1""").param("procedure", procedure).query(String.class).single();
        return json.readValue(data, RequestData.class);
    }
}
