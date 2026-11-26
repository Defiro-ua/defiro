package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FieldProblemTest {

    @Test
    void aProblemNamesItsFieldAndWhatIsWrong() {
        FieldProblem problem = new FieldProblem("nif", "O NIF tem 9 algarismos.");

        assertThat(problem.field()).isEqualTo("nif");
        assertThat(problem.message()).isEqualTo("O NIF tem 9 algarismos.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aProblemBelongsToAField(String field) {
        assertThatThrownBy(() -> new FieldProblem(field, "O NIF tem 9 algarismos."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aProblemSaysWhatIsWrong(String message) {
        assertThatThrownBy(() -> new FieldProblem("nif", message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }
}
