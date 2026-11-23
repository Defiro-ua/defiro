package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DecisionTest {

    static final Person RUI = new Person("rui", "Rui Matos", "Chefe de Divisão", null, null);

    @Test
    void submittingIsADecisionWithTheData() {
        Decision decision = Decision.submit(Map.of("destino", "Lisboa"));

        assertThat(decision.action()).isEqualTo("submit");
        assertThat(decision.data()).containsEntry("destino", "Lisboa");
        assertThat(decision.reasons()).isEmpty();
        assertThat(decision.notes()).isEmpty();
        assertThat(decision.message()).isNull();
    }

    @Test
    void aReturnKeepsItsReasonsMessageAndNotes() {
        FieldNote note = new FieldNote("iban", "O IBAN não corresponde ao seu nome.", RUI);

        Decision decision = new Decision(
                "return", null, List.of("Incorrectly filled"), "Corrija o IBAN, por favor.", List.of(note));

        assertThat(decision.data()).isEmpty();
        assertThat(decision.reasons()).containsExactly("Incorrectly filled");
        assertThat(decision.message()).isEqualTo("Corrija o IBAN, por favor.");
        assertThat(decision.notes()).containsExactly(note);
    }

    @Test
    void theDecisionCannotChangeAfterItIsMade() {
        List<String> reasons = new ArrayList<>(List.of("Incorrectly filled"));
        Decision decision = new Decision("return", Map.of(), reasons, null, null);

        reasons.add("Missing receipt");

        assertThat(decision.reasons()).containsExactly("Incorrectly filled");
        assertThatThrownBy(() -> decision.reasons().add("Missing receipt"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> decision.data().put("destino", "Porto"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aDecisionNeedsAnAction(String action) {
        assertThatThrownBy(() -> new Decision(action, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");
    }
}
