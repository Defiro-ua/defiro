package io.github.defiro.designer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.designer.workflows.PublishedWorkflow;
import io.github.defiro.designer.workflows.PublishedWorkflows;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

// The versions of each workflow live in the Designer, as the team decided; the Engine keeps only
// the procedures (instances), and reads the version in force through PublishedWorkflows.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WorkflowVersionsIT {

    static final Publisher.Who SOFIA = new Publisher.Who("sofia.almeida", "Sofia Almeida");

    @Autowired
    WorkflowVersions versions;

    @Autowired
    PublishedWorkflows published;

    @Autowired
    Drafts drafts;

    @Autowired
    JdbcClient jdbc;

    UUID workflow;

    @BeforeEach
    void aWorkflowIsADraftFirst() {
        workflow = newWorkflow();
    }

    @Test
    void theFirstPublicationIsVersionOne() {
        assertThat(versions.add(workflow, 1, specification("Ajudas de custo"), SOFIA))
                .isEqualTo(1);
    }

    @Test
    void aNewRevisionOfTheDraftIsTheNextVersion() {
        versions.add(workflow, 1, specification("Ajudas de custo"), SOFIA);

        assertThat(versions.add(workflow, 4, specification("Ajudas de custo"), SOFIA))
                .isEqualTo(2);
    }

    @Test
    void publishingTheSameRevisionAgainGivesTheSameVersion() {
        versions.add(workflow, 1, specification("Ajudas de custo"), SOFIA);

        assertThat(versions.add(workflow, 1, specification("Ajudas de custo"), SOFIA))
                .isEqualTo(1);
    }

    @Test
    void eachWorkflowCountsItsOwnVersions() {
        versions.add(workflow, 1, specification("Ajudas de custo"), SOFIA);

        assertThat(versions.add(newWorkflow(), 1, specification("Horas extraordinárias"), SOFIA))
                .isEqualTo(1);
    }

    @Test
    void aVersionKeepsTheSpecificationItWasPublishedWith() {
        WorkflowSpecification first = specification("Ajudas de custo");
        versions.add(workflow, 1, first, SOFIA);
        versions.add(workflow, 2, specification("Ajudas de custo e transporte"), SOFIA);

        assertThat(versions.find(workflow, 1)).contains(first);
    }

    @Test
    void theEngineGetsTheVersionInForce() {
        versions.add(workflow, 1, specification("Ajudas de custo"), SOFIA);
        WorkflowSpecification second = specification("Ajudas de custo e transporte");
        versions.add(workflow, 2, second, SOFIA);

        assertThat(published.latest(workflow)).contains(new PublishedWorkflow(workflow, 2, second));
    }

    @Test
    void aWorkflowNeverPublishedHasNoVersionInForce() {
        assertThat(published.latest(UUID.randomUUID())).isEmpty();
    }

    @Test
    void theEngineListsTheVersionInForceOfEachWorkflow() {
        UUID other = newWorkflow();
        versions.add(workflow, 1, specification("Ajudas de custo"), SOFIA);
        versions.add(workflow, 2, specification("Ajudas de custo e transporte"), SOFIA);
        versions.add(other, 1, specification("Horas extraordinárias"), SOFIA);

        assertThat(published.inForce())
                .filteredOn(each ->
                        each.workflow().equals(workflow) || each.workflow().equals(other))
                .extracting(PublishedWorkflow::workflow, PublishedWorkflow::version)
                .containsExactlyInAnyOrder(tuple(workflow, 2), tuple(other, 1));
    }

    @Test
    void aPublishedVersionCannotBeChanged() {
        versions.add(workflow, 1, specification("Ajudas de custo"), SOFIA);

        assertThatThrownBy(() -> jdbc.sql(
                                "update designer.workflow_version set published_by_name = 'Outra pessoa' where workflow = :workflow")
                        .param("workflow", workflow)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Uma versão publicada nunca muda");
        assertThat(versions.find(workflow, 1)).contains(specification("Ajudas de custo"));
    }

    @Test
    void aPublishedVersionCannotBeDeleted() {
        versions.add(workflow, 1, specification("Ajudas de custo"), SOFIA);

        assertThatThrownBy(() -> jdbc.sql("delete from designer.workflow_version where workflow = :workflow")
                        .param("workflow", workflow)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Uma versão publicada nunca muda");
        assertThat(published.latest(workflow)).isPresent();
    }

    UUID newWorkflow() {
        return drafts.add(WorkflowDraftTest.withSteps()).id();
    }

    static WorkflowSpecification specification(String name) {
        StepSpecification request = new StepSpecification(
                "pedido",
                new PluginRef("submission", "0.1.0"),
                "Pedido",
                "Todos os trabalhadores",
                null,
                List.of(),
                List.of());
        return new WorkflowSpecification(WorkflowSpecification.SCHEMA_VERSION, name, List.of(request), List.of());
    }
}
