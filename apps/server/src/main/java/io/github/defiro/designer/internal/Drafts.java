package io.github.defiro.designer.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** The drafts in the {@code designer} schema, each kept whole as JSON. */
@Repository
class Drafts {

    // Each draft with its latest version, if it was ever published.
    private static final String DRAFTS = """
            select d.id, d.revision, d.content,
                   v.version, v.published_by, v.published_by_name, v.published_at
            from designer.draft d
            left join lateral (
                select version, published_by, published_by_name, published_at
                from designer.workflow_version
                where workflow = d.id
                order by version desc
                limit 1) v on true""";

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
        int saved = jdbc.sql("update designer.draft set revision = revision + 1, content = cast(:content as jsonb)"
                        + " where id = :id")
                .param("id", id)
                .param("content", json.writeValueAsString(draft))
                .update();
        return saved == 0 ? Optional.empty() : find(id);
    }

    Optional<StoredDraft> find(UUID id) {
        return jdbc.sql(DRAFTS + " where d.id = :id")
                .param("id", id)
                .query(this::stored)
                .optional();
    }

    List<StoredDraft> all() {
        return jdbc.sql(DRAFTS + " order by d.content ->> 'name', d.id")
                .query(this::stored)
                .list();
    }

    private StoredDraft stored(ResultSet row, int number) throws SQLException {
        return new StoredDraft(
                row.getObject("id", UUID.class),
                row.getInt("revision"),
                json.readValue(row.getString("content"), WorkflowDraft.class),
                published(row));
    }

    private static Published published(ResultSet row) throws SQLException {
        Integer version = row.getObject("version", Integer.class);
        if (version == null) {
            return null;
        }
        return new Published(
                version,
                row.getString("published_by"),
                row.getString("published_by_name"),
                row.getObject("published_at", OffsetDateTime.class).toInstant());
    }
}
