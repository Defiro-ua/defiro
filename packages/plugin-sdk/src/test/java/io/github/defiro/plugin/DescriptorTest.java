package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DescriptorTest {

    @Test
    void aDescriptorSaysWhatThePluginIsHowItIsConfiguredAndHowItCanEnd() {
        Descriptor review = new Descriptor(
                "review", "0.1.0", List.of("assignee", "deadline"), List.of("approved", "returned", "rejected"));

        assertThat(review.name()).isEqualTo("review");
        assertThat(review.version()).isEqualTo("0.1.0");
        assertThat(review.configFields()).containsExactly("assignee", "deadline");
        assertThat(review.outcomes()).containsExactly("approved", "returned", "rejected");
    }

    @Test
    void aPluginWithNothingToConfigureHasNoConfigFields() {
        assertThat(new Descriptor("submission", "0.1.0", null, List.of("submitted")).configFields())
                .isEmpty();
    }

    @Test
    void theListsCannotChangeAfterwards() {
        List<String> outcomes = new ArrayList<>(List.of("approved"));
        Descriptor review = new Descriptor("review", "0.1.0", List.of(), outcomes);

        outcomes.add("rejected");

        assertThat(review.outcomes()).containsExactly("approved");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aDescriptorNamesThePlugin(String name) {
        assertThatThrownBy(() -> new Descriptor(name, "0.1.0", List.of(), List.of("done")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aDescriptorGivesTheVersion(String version) {
        assertThatThrownBy(() -> new Descriptor("review", version, List.of(), List.of("done")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void aPluginEndsWithAtLeastOneOutcome() {
        assertThatThrownBy(() -> new Descriptor("review", "0.1.0", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
        assertThatThrownBy(() -> new Descriptor("review", "0.1.0", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
    }
}
