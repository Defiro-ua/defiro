package io.github.defiro.designer.internal;

import io.github.defiro.designer.workflows.PublishedWorkflow;
import io.github.defiro.designer.workflows.PublishedWorkflows;
import io.github.defiro.spec.WorkflowSpecification;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** The versions of each workflow, in the {@code designer} schema. Versions are never changed. */
@Repository
class WorkflowVersions implements PublishedWorkflows {

    private static final String LATEST = """
            select distinct on (workflow) workflow, version, specification
            from designer.workflow_version""";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    WorkflowVersions(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Keeps the specification as the workflow's next version. Safe to retry: the same revision of
     * the draft gives back the version it already has.
     *
     * @return the version number, starting at 1 for each workflow
     */
    @Transactional
    int add(UUID workflow, int revision, WorkflowSpecification specification, Publisher.Who who) {
        Optional<Integer> existing = jdbc.sql(
                        "select version from designer.workflow_version where workflow = :workflow and revision = :revision")
                .param("workflow", workflow)
                .param("revision", revision)
                .query(Integer.class)
                .optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        int version = jdbc.sql(
                        "select coalesce(max(version), 0) + 1 from designer.workflow_version where workflow = :workflow")
                .param("workflow", workflow)
                .query(Integer.class)
                .single();
        jdbc.sql("""
                        insert into designer.workflow_version
                            (workflow, version, revision, published_by, published_by_name, specification)
                        values (:workflow, :version, :revision, :by, :byName, cast(:specification as jsonb))""")
                .param("workflow", workflow)
                .param("version", version)
                .param("revision", revision)
                .param("by", who.id())
                .param("byName", who.name())
                .param("specification", json.writeValueAsString(specification))
                .update();
        return version;
    }

    Optional<WorkflowSpecification> find(UUID workflow, int version) {
        return jdbc.sql(
                        "select specification from designer.workflow_version where workflow = :workflow and version = :version")
                .param("workflow", workflow)
                .param("version", version)
                .query(String.class)
                .optional()
                .map(stored -> json.readValue(stored, WorkflowSpecification.class));
    }

    @Override
    public Optional<PublishedWorkflow> latest(UUID workflow) {
        return jdbc.sql(LATEST + " where workflow = :workflow order by workflow, version desc")
                .param("workflow", workflow)
                .query(this::published)
                .optional();
    }

    @Override
    public List<PublishedWorkflow> inForce() {
        return jdbc.sql(LATEST + " order by workflow, version desc")
                .query(this::published)
                .list();
    }

    private PublishedWorkflow published(ResultSet row, int number) throws SQLException {
        return new PublishedWorkflow(
                row.getObject("workflow", UUID.class),
                row.getInt("version"),
                json.readValue(row.getString("specification"), WorkflowSpecification.class));
    }
}
