package io.github.defiro.designer.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** The workflows in the {@code designer} schema. */
@Repository
class Workflows {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    Workflows(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void add(Workflow workflow) {
        jdbc.sql("""
                        insert into designer.workflow (id, name, version, status, definition)
                        values (:id, :name, :version, :status, cast(:definition as jsonb))""")
                .param("id", workflow.id())
                .param("name", workflow.name())
                .param("version", workflow.version())
                .param("status", workflow.status().name())
                .param("definition", json.writeValueAsString(workflow.definition()))
                .update();
    }

    List<Workflow> all() {
        return jdbc.sql("select id, name, version, status, definition from designer.workflow order by name, version")
                .query((row, n) -> new Workflow(
                        row.getObject("id", UUID.class),
                        row.getString("name"),
                        row.getInt("version"),
                        WorkflowStatus.valueOf(row.getString("status")),
                        json.readValue(row.getString("definition"), WorkflowDraft.class)))
                .list();
    }
}
