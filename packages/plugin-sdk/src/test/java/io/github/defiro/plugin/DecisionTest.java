package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DecisionTest {

    @Test
    void aDecisionKeepsItsReasons() {
        Decision decision = new Decision("returned", List.of("Incorrectly filled", "Missing receipt"));

        assertThat(decision.action()).isEqualTo("returned");
        assertThat(decision.reasons()).containsExactly("Incorrectly filled", "Missing receipt");
    }

    @Test
    void aDecisionWithNoReasonsHasAnEmptyList() {
        assertThat(new Decision("submitted", null).reasons()).isEmpty();
    }

    @Test
    void theReasonsCannotChangeAfterTheDecision() {
        List<String> reasons = new ArrayList<>(List.of("Incorrectly filled"));
        Decision decision = new Decision("returned", reasons);

        reasons.add("Missing receipt");

        assertThat(decision.reasons()).containsExactly("Incorrectly filled");
        assertThatThrownBy(() -> decision.reasons().add("Missing receipt"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aDecisionNeedsAnAction(String action) {
        assertThatThrownBy(() -> new Decision(action, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }
}
