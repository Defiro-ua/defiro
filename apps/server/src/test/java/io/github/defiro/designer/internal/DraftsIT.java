package io.github.defiro.designer.internal;

import static io.github.defiro.designer.internal.WorkflowDraftTest.complete;
import static io.github.defiro.designer.internal.WorkflowDraftTest.withSteps;
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
    void aNewDraftIsTheFirstRevisionAndUnpublished() {
        StoredDraft added = drafts.add(withSteps(complete("Validação da chefia")));

        assertThat(drafts.find(added.id())).contains(added);
        assertThat(added.revision()).isEqualTo(1);
        assertThat(added.publishedVersion()).isNull();
    }

    @Test
    void savingKeepsTheWholeDraftAsTheNextRevision() {
        UUID id = drafts.add(withSteps()).id();
        WorkflowDraft changed = withSteps(complete("Validação da chefia"), complete("Verificação financeira"));

        StoredDraft saved = drafts.save(id, changed).orElseThrow();

        assertThat(saved.revision()).isEqualTo(2);
        assertThat(drafts.find(id).orElseThrow().draft()).isEqualTo(changed);
    }

    @Test
    void anUnknownDraftIsNeitherFoundNorSaved() {
        assertThat(drafts.find(UUID.randomUUID())).isEmpty();
        assertThat(drafts.save(UUID.randomUUID(), withSteps())).isEmpty();
    }

    @Test
    void aPublishedDraftKeepsTheVersionTheEngineGave() {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();

        drafts.markPublished(id, 1, 3);

        assertThat(drafts.find(id).orElseThrow().publishedVersion()).isEqualTo(3);
        assertThat(drafts.all()).extracting(StoredDraft::id).contains(id);
    }
}
