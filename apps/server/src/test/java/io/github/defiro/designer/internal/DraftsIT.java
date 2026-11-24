package io.github.defiro.designer.internal;

import static io.github.defiro.designer.internal.WorkflowDraftTest.complete;
import static io.github.defiro.designer.internal.WorkflowDraftTest.withSteps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.defiro.TestPeople;
import io.github.defiro.TestcontainersConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DraftsIT {

    @Autowired
    Drafts drafts;

    @Autowired
    JdbcClient jdbc;

    UUID sofia;

    @BeforeEach
    void sofiaIsRegistered() {
        TestPeople people = new TestPeople(jdbc);
        people.clear();
        sofia = people.register("Sofia Almeida", "sofia.almeida@cm.test");
    }

    @Test
    void aNewDraftIsTheFirstRevisionAndUnpublished() {
        StoredDraft added = drafts.add(withSteps(complete("Validação da chefia")));

        assertThat(drafts.find(added.id())).contains(added);
        assertThat(added.revision()).isEqualTo(1);
        assertThat(added.published()).isNull();
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
    void aPublishedDraftKeepsTheVersionTheEngineGaveAndWhoPublishedItWhen() {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();

        drafts.markPublished(id, 1, 3, sofia);

        Published published = drafts.find(id).orElseThrow().published();
        assertThat(published.version()).isEqualTo(3);
        assertThat(published.by()).isEqualTo(sofia);
        assertThat(published.byName()).isEqualTo("Sofia Almeida");
        assertThat(published.at()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        assertThat(drafts.all()).extracting(StoredDraft::id).contains(id);
    }

    @Test
    void aDraftNeverPublishedHasNoPublication() {
        UUID id = drafts.add(withSteps()).id();

        assertThat(drafts.find(id).orElseThrow().published()).isNull();
    }
}
