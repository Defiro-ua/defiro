package io.github.defiro.engine.internal;

import io.github.defiro.spec.StepSpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * TEST ONLY. Stands in for UC13 (Dinis), the request form: it keeps a submitted request the way the
 * Engine will, so the review stories (UC17, UC18, UC19) can run before UC13 exists. When UC13 does,
 * the acceptance tests submit through it and this class goes away.
 *
 * <p>The document keeps what {@link RequestData} says.
 */
@Component
public class ProcedureSeed {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Files files;
    private final Tasks tasks;

    ProcedureSeed(JdbcClient jdbc, JsonMapper json, Files files, Tasks tasks) {
        this.jdbc = jdbc;
        this.json = json;
        this.files = files;
        this.tasks = tasks;
    }

    /** The submitted request, and the task of the step it waits at. */
    public record Submitted(UUID procedure, UUID document, UUID task) {}

    /**
     * A request submitted on the given step, waiting at the step after it, in the inbox of that
     * step's candidates. A request with the same reference from an earlier scenario is removed first.
     */
    @Transactional
    public Submitted submit(
            String reference,
            UUID draft,
            int version,
            String requestStep,
            StepSpecification nextStep,
            RequestData.Requester requester,
            List<RequestData.Answer> answers) {
        forget(reference);
        Submitted submitted = new Submitted(UUID.randomUUID(), UUID.randomUUID(), null);
        jdbc.sql("""
                        insert into engine.procedure (id, reference, draft, version, requester_id, current_step_id, status)
                        values (:id, :reference, :draft, :version, :requester, :step, 'in_progress')""")
                .param("id", submitted.procedure())
                .param("reference", reference)
                .param("draft", draft)
                .param("version", version)
                .param("requester", requester.id())
                .param("step", nextStep.id())
                .update();
        jdbc.sql("""
                        insert into engine.document (id, procedure_id, step_id, data, status)
                        values (:id, :procedure, :step, cast(:data as jsonb), 'submitted')""")
                .param("id", submitted.document())
                .param("procedure", submitted.procedure())
                .param("step", requestStep)
                .param("data", json.writeValueAsString(new RequestData(requester, answers)))
                .update();
        jdbc.sql("""
                        insert into engine.audit_log (actor_id, procedure_id, action, entity_type, entity_id, details)
                        values (:actor, :procedure, 'submitted', 'document', :document, cast(:details as jsonb))""")
                .param("actor", requester.id())
                .param("procedure", submitted.procedure())
                .param("document", submitted.document().toString())
                .param(
                        "details",
                        json.writeValueAsString(Map.of("name", requester.name(), "jobTitle", requester.jobTitle())))
                .update();
        UUID task = tasks.open(submitted.procedure(), nextStep);
        return new Submitted(submitted.procedure(), submitted.document(), task);
    }

    /** A file attached to the submitted request, as UC15 (Dinis) will do. */
    public UUID attach(Submitted request, String filename, byte[] content) {
        return files.store(request.procedure(), request.document(), filename, content)
                .id();
    }

    private void forget(String reference) {
        jdbc.sql("""
                        delete from engine.file_content where storage_key in (
                            select f.storage_key from engine.file f
                            join engine.procedure p on p.id = f.procedure_id
                            where p.reference = :reference)""").param("reference", reference).update();
        for (String table : List.of("file", "audit_log", "task", "document")) {
            jdbc.sql("delete from engine." + table
                            + " where procedure_id in (select id from engine.procedure where reference = :reference)")
                    .param("reference", reference)
                    .update();
        }
        jdbc.sql("delete from engine.procedure where reference = :reference")
                .param("reference", reference)
                .update();
    }
}
