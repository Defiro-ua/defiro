package io.github.defiro.designer.internal;

import static io.github.defiro.designer.internal.WorkflowDraftTest.complete;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WorkflowsIT {

    @Autowired
    Workflows workflows;

    @Test
    void anAddedWorkflowIsListedAsItWas() {
        Workflow workflow =
                Workflow.firstVersionOf(WorkflowDraft.named("Ajudas de custo").with(complete("Validação da chefia")));

        workflows.add(workflow);

        assertThat(workflows.all()).contains(workflow);
    }
}
