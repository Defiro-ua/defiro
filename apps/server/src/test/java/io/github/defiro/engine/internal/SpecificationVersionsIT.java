package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;

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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpecificationVersionsIT {

    @Autowired
    Deployment deployment;

    @Autowired
    SpecificationVersions versions;

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

        assertThat(versions.find(draft, 1)).contains(first);
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
