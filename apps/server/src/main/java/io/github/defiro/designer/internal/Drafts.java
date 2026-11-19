package io.github.defiro.designer.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** The Designer store: drafts in the {@code designer} schema, each kept whole as JSON. */
@Repository
class Drafts {

    private static final String COLUMNS = "id, revision, content, published_version";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    Drafts(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    StoredDraft add(WorkflowDraft draft) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into designer.draft (id, revision, content) values (:id, 1, cast(:content as jsonb))")
                .param("id", id)
                .param("content", json.writeValueAsString(draft))
                .update();
        return new StoredDraft(id, 1, draft, null);
    }

    /** Saves the draft whole as its next revision; empty if there is no such draft. */
    Optional<StoredDraft> save(UUID id, WorkflowDraft draft) {
        return jdbc.sql("update designer.draft set revision = revision + 1, content = cast(:content as jsonb)"
                        + " where id = :id returning " + COLUMNS)
                .param("id", id)
                .param("content", json.writeValueAsString(draft))
                .query(this::stored)
                .optional();
    }

    Optional<StoredDraft> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from designer.draft where id = :id")
                .param("id", id)
                .query(this::stored)
                .optional();
    }

    List<StoredDraft> all() {
        return jdbc.sql("select " + COLUMNS + " from designer.draft order by content ->> 'name', id")
                .query(this::stored)
                .list();
    }

    void markPublished(UUID id, int revision, int version) {
        jdbc.sql("update designer.draft set published_revision = :revision, published_version = :version"
                        + " where id = :id")
                .param("id", id)
                .param("revision", revision)
                .param("version", version)
                .update();
    }

    private StoredDraft stored(ResultSet row, int number) throws SQLException {
        return new StoredDraft(
                row.getObject("id", UUID.class),
                row.getInt("revision"),
                json.readValue(row.getString("content"), WorkflowDraft.class),
                row.getObject("published_version", Integer.class));
    }
}
