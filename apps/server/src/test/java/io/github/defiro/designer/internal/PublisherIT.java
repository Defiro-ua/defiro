package io.github.defiro.designer.internal;

import static io.github.defiro.designer.internal.WorkflowDraftTest.complete;
import static io.github.defiro.designer.internal.WorkflowDraftTest.withSteps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestPeople;
import io.github.defiro.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PublisherIT {

    @Autowired
    Publisher publisher;

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
    void publishingMarksTheDraftWithTheVersionAndWhoPublishedIt() {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();

        assertThat(publisher.publish(id, sofia)).contains(1);

        Published published = drafts.find(id).orElseThrow().published();
        assertThat(published.version()).isEqualTo(1);
        assertThat(published.by()).isEqualTo(sofia);
        assertThat(published.byName()).isEqualTo("Sofia Almeida");
    }

    @Test
    void publishingAgainAfterSavingIsTheNextVersion() {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();
        publisher.publish(id, sofia);
        drafts.save(id, withSteps(complete("Validação da chefia"), complete("Verificação financeira")));

        assertThat(publisher.publish(id, sofia)).contains(2);
    }

    @Test
    void anIncompleteDraftIsNotPublished() {
        UUID id = drafts.add(withSteps()).id();
        drafts.save(
                id, new WorkflowDraft("", drafts.find(id).orElseThrow().draft().steps()));

        assertThatThrownBy(() -> publisher.publish(id, sofia)).isInstanceOf(IncompleteWorkflowException.class);
        assertThat(drafts.find(id).orElseThrow().published()).isNull();
    }

    @Test
    void anUnknownDraftIsNotPublished() {
        assertThat(publisher.publish(UUID.randomUUID(), sofia)).isEmpty();
    }
}
