package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpecificationVersionsIT {

    @Autowired
    Deployment deployment;

    @Autowired
    JdbcClient jdbc;

    final UUID draft = UUID.randomUUID();

    @Test
    void theFirstDeploymentIsVersionOne() {
        assertThat(deployment.deploy(specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1)))
                .isEqualTo(1);
    }

    @Test
    void aNewRevisionOfTheDraftIsTheNextVersion() {
        deployment.deploy(specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1));

        assertThat(deployment.deploy(specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 4)))
                .isEqualTo(2);
    }

    @Test
    void deployingTheSameRevisionAgainGivesTheSameVersion() {
        deployment.deploy(specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1));

        assertThat(deployment.deploy(specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1)))
                .isEqualTo(1);
    }

    @Test
    void eachWorkflowCountsItsOwnVersions() {
        deployment.deploy(specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1));

        assertThat(deployment.deploy(
                        specification("Horas extraordinárias"), new Publication("sofia.almeida", UUID.randomUUID(), 1)))
                .isEqualTo(1);
    }

    @Test
    void aVersionKeepsTheSpecificationItWasDeployedWith() {
        WorkflowSpecification first = specification("Ajudas de custo");
        deployment.deploy(first, new Publication("sofia.almeida", draft, 1));
        deployment.deploy(specification("Ajudas de custo e transporte"), new Publication("sofia.almeida", draft, 2));

        assertThat(deployment.version(draft, 1)).contains(first);
    }

    @Test
    void aDeployedVersionCannotBeChanged() {
        deployment.deploy(specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1));

        assertThatThrownBy(() -> jdbc.sql(
                                "update engine.specification_version set published_by = 'outra.pessoa' where draft = :draft")
                        .param("draft", draft)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Uma versão publicada nunca muda");
        assertThat(deployment.version(draft, 1)).contains(specification("Ajudas de custo"));
    }

    @Test
    void aDeployedVersionCannotBeDeleted() {
        deployment.deploy(specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1));

        assertThatThrownBy(() -> jdbc.sql("delete from engine.specification_version where draft = :draft")
                        .param("draft", draft)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Uma versão publicada nunca muda");
        assertThat(deployment.version(draft, 1)).isPresent();
    }

    @Test
    void aVersionNeverDeployedIsNotFound() {
        deployment.deploy(specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1));

        assertThat(deployment.version(draft, 2)).isEmpty();
        assertThat(deployment.version(UUID.randomUUID(), 1)).isEmpty();
    }

    static WorkflowSpecification specification(String name) {
        StepSpecification request = new StepSpecification(
                "pedido",
                new PluginRef("submission", "0.1.0"),
                "Pedido",
                "Todos os trabalhadores",
                null,
                List.of(),
                List.of(),
                List.of());
        return new WorkflowSpecification(WorkflowSpecification.SCHEMA_VERSION, name, List.of(request), List.of());
    }
}
