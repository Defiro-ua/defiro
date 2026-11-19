package io.github.defiro.designer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DraftsIT {

    @Autowired
    Drafts drafts;

    @Test
    void aSavedDraftComesBackAsItWas() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo")
                .with(Step.review("Validação da chefia", "Chefias da unidade do requerente", 5));
        UUID id = drafts.add(draft);

        assertThat(drafts.find(id)).contains(draft);
    }

    @Test
    void aSavedDraftCanBeReplaced() {
        UUID id = drafts.add(WorkflowDraft.named("Ajudas de custo"));
        WorkflowDraft renamed = WorkflowDraft.named("Horas extraordinárias");

        drafts.replace(id, renamed);

        assertThat(drafts.find(id)).contains(renamed);
    }

    @Test
    void anUnknownDraftIsNotFound() {
        assertThat(drafts.find(UUID.randomUUID())).isEmpty();
    }
}
