package io.github.defiro.engine.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who did what to a procedure, and when. UC17 writes its decisions here; the history screen and its
 * rules (order, entries never change, who sees it) are UC25 (Bernardo).
 */
@Repository
class AuditLog {

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditLog(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** One entry. The details keep the person's name and job title at the time, and what the action needs. */
    record Entry(String actor, String action, Map<String, Object> details, OffsetDateTime at) {}

    /** Adds an entry about something of the procedure, such as a task or a document. */
    void append(
            String actor, UUID procedure, String action, String entityType, UUID entity, Map<String, Object> details) {
        jdbc.sql("""
                        insert into engine.audit_log (actor_id, procedure_id, action, entity_type, entity_id, details)
                        values (:actor, :procedure, :action, :type, :entity, cast(:details as jsonb))""")
                .param("actor", actor)
                .param("procedure", procedure)
                .param("action", action)
                .param("type", entityType)
                .param("entity", entity.toString())
                .param("details", json.writeValueAsString(details))
                .update();
    }

    /** The newest entry with this action about this entity, such as the decision on a task. */
    Optional<Entry> last(String action, UUID entity) {
        return jdbc.sql("""
                        select actor_id, action, details, occurred_at from engine.audit_log
                        where action = :action and entity_id = :entity order by id desc limit 1""")
                .param("action", action)
                .param("entity", entity.toString())
                .query(this::entry)
                .optional();
    }

    /** The procedure's newest entry with this action. */
    Optional<Entry> latest(UUID procedure, String action) {
        return jdbc.sql("""
                        select actor_id, action, details, occurred_at from engine.audit_log
                        where procedure_id = :procedure and action = :action order by id desc limit 1""")
                .param("procedure", procedure)
                .param("action", action)
                .query(this::entry)
                .optional();
    }

    /** The procedure's entries, the oldest first. */
    List<Entry> of(UUID procedure) {
        return jdbc.sql("""
                        select actor_id, action, details, occurred_at from engine.audit_log
                        where procedure_id = :procedure order by occurred_at, id""").param("procedure", procedure).query(this::entry).list();
    }

    private Entry entry(ResultSet row, int n) throws SQLException {
        return new Entry(
                row.getString("actor_id"),
                row.getString("action"),
                details(row.getString("details")),
                row.getObject("occurred_at", OffsetDateTime.class));
    }

    private Map<String, Object> details(String stored) {
        return stored == null ? Map.of() : json.readValue(stored, DETAILS);
    }
}
