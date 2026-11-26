package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EntryTest {

    static final GeneratedDocument ORDER =
            new GeneratedDocument("ordem-de-pagamento.pdf", "application/pdf", new byte[] {1, 2, 3});

    @Test
    void aStepThatWaitsSaysWhoShouldAct() {
        Entry entry = new Entry.Wait(new AssigneeRule.Group("divisao-financeira"));

        assertThat(entry).isEqualTo(new Entry.Wait(new AssigneeRule.Group("divisao-financeira")));
    }

    @Test
    void aStepCannotWaitForNobody() {
        assertThatThrownBy(() -> new Entry.Wait(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("who should act");
    }

    @Test
    void aStepThatIsDoneAtOnceSaysHowItEndedAndWhatItGenerated() {
        Entry.Done done = new Entry.Done("done", List.of(ORDER));

        assertThat(done.outcome()).isEqualTo("done");
        assertThat(done.documents()).containsExactly(ORDER);
    }

    @Test
    void aStepThatGeneratesNothingHasNoDocuments() {
        assertThat(new Entry.Done("done", null).documents()).isEmpty();
    }

    @Test
    void theDocumentsCannotChangeAfterTheStepIsDone() {
        List<GeneratedDocument> documents = new ArrayList<>(List.of(ORDER));
        Entry.Done done = new Entry.Done("done", documents);

        documents.clear();

        assertThat(done.documents()).containsExactly(ORDER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aStepCannotBeDoneWithoutAnOutcome(String outcome) {
        assertThatThrownBy(() -> new Entry.Done(outcome, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
    }
}
