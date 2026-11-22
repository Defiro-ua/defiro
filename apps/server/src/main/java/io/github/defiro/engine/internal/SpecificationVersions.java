package io.github.defiro.engine.internal;

import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** The specification versions in the {@code engine} schema. */
@Repository
class SpecificationVersions implements Deployment {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    SpecificationVersions(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public int deploy(WorkflowSpecification specification, Publication publication) {
        Optional<Integer> existing = jdbc.sql(
                        "select version from engine.specification_version where draft = :draft and revision = :revision")
                .param("draft", publication.draft())
                .param("revision", publication.revision())
                .query(Integer.class)
                .optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        int version = jdbc.sql(
                        "select coalesce(max(version), 0) + 1 from engine.specification_version where draft = :draft")
                .param("draft", publication.draft())
                .query(Integer.class)
                .single();
        jdbc.sql("""
                        insert into engine.specification_version
                            (draft, version, revision, published_by, specification)
                        values (:draft, :version, :revision, :publishedBy, cast(:specification as jsonb))""")
                .param("draft", publication.draft())
                .param("version", version)
                .param("revision", publication.revision())
                .param("publishedBy", publication.publishedBy())
                .param("specification", json.writeValueAsString(specification))
                .update();
        return version;
    }

    @Override
    public Optional<WorkflowSpecification> version(UUID draft, int version) {
        return jdbc.sql(
                        "select specification from engine.specification_version where draft = :draft and version = :version")
                .param("draft", draft)
                .param("version", version)
                .query(String.class)
                .optional()
                .map(stored -> json.readValue(stored, WorkflowSpecification.class));
    }
}
