package io.github.defiro.designer.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** The drafts in the {@code designer} schema, each kept whole as JSON. */
@Repository
class Drafts {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    Drafts(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    UUID add(WorkflowDraft draft) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into designer.draft (id, content) values (:id, cast(:content as jsonb))")
                .param("id", id)
                .param("content", json.writeValueAsString(draft))
                .update();
        return id;
    }

    void replace(UUID id, WorkflowDraft draft) {
        jdbc.sql("update designer.draft set content = cast(:content as jsonb) where id = :id")
                .param("id", id)
                .param("content", json.writeValueAsString(draft))
                .update();
    }

    Optional<WorkflowDraft> find(UUID id) {
        return jdbc.sql("select content from designer.draft where id = :id")
                .param("id", id)
                .query(String.class)
                .optional()
                .map(content -> json.readValue(content, WorkflowDraft.class));
    }
}
