package io.github.defiro.designer.internal;

import static io.github.defiro.designer.internal.WorkflowDraftTest.complete;
import static io.github.defiro.designer.internal.WorkflowDraftTest.withSteps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PublisherIT {

    static final Publisher.Who SOFIA = new Publisher.Who("sofia.almeida", "Sofia Almeida");

    @Autowired
    Publisher publisher;

    @Autowired
    Drafts drafts;

    @Test
    void publishingMarksTheDraftWithTheVersionAndWhoPublishedIt() {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();

        assertThat(publisher.publish(id, SOFIA)).contains(1);

        Published published = drafts.find(id).orElseThrow().published();
        assertThat(published.version()).isEqualTo(1);
        assertThat(published.by()).isEqualTo("sofia.almeida");
        assertThat(published.byName()).isEqualTo("Sofia Almeida");
    }

    @Test
    void publishingAgainAfterSavingIsTheNextVersion() {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();
        publisher.publish(id, SOFIA);
        drafts.save(id, withSteps(complete("Validação da chefia"), complete("Verificação financeira")));

        assertThat(publisher.publish(id, SOFIA)).contains(2);
    }

    @Test
    void anIncompleteDraftIsNotPublished() {
        UUID id = drafts.add(withSteps()).id();
        drafts.save(
                id, new WorkflowDraft("", drafts.find(id).orElseThrow().draft().steps()));

        assertThatThrownBy(() -> publisher.publish(id, SOFIA)).isInstanceOf(IncompleteWorkflowException.class);
        assertThat(drafts.find(id).orElseThrow().published()).isNull();
    }

    @Test
    void anUnknownDraftIsNotPublished() {
        assertThat(publisher.publish(UUID.randomUUID(), SOFIA)).isEmpty();
    }
}
