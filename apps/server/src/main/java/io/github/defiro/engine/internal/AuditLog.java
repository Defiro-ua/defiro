package io.github.defiro.engine.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

    /** The procedure's entries, the oldest first. */
    List<Entry> of(UUID procedure) {
        return jdbc.sql("""
                        select actor_id, action, details, occurred_at from engine.audit_log
                        where procedure_id = :procedure order by occurred_at, id""")
                .param("procedure", procedure)
                .query((row, n) -> new Entry(
                        row.getString("actor_id"),
                        row.getString("action"),
                        details(row.getString("details")),
                        row.getObject("occurred_at", OffsetDateTime.class)))
                .list();
    }

    private Map<String, Object> details(String stored) {
        return stored == null ? Map.of() : json.readValue(stored, DETAILS);
    }
}
