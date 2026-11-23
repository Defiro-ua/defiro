package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EntryTest {

    @Test
    void aStepThatWaitsSaysWhoHandlesIt() {
        Entry entry = new Entry.Wait("divisao-financeira");

        assertThat(entry).isEqualTo(new Entry.Wait("divisao-financeira"));
        assertThat(((Entry.Wait) entry).handledBy()).isEqualTo("divisao-financeira");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aStepCannotWaitForNobody(String handledBy) {
        assertThatThrownBy(() -> new Entry.Wait(handledBy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("who handles");
    }

    @Test
    void aStepThatIsDoneAtOnceSaysHowItEnded() {
        Entry entry = new Entry.Done("generated");

        assertThat(((Entry.Done) entry).outcome()).isEqualTo("generated");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aStepCannotBeDoneWithoutAnOutcome(String outcome) {
        assertThatThrownBy(() -> new Entry.Done(outcome))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
    }
}
