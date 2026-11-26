package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StepViewTest {

    static final Person RUI = new Person("rui", "Rui Matos", "Chefe de Divisão", null, null);

    @Test
    void theViewOfAStepHasItsFieldsChecksAndNotes() {
        ViewField name = new ViewField("nome", "Ana Ferreira", true);
        CheckResult dates = new CheckResult("Datas do certificado", false, "Não coincide com a deslocação");
        FieldNote note = new FieldNote("iban", "O IBAN não corresponde ao seu nome.", RUI);

        StepView view = new StepView(List.of(name), List.of(dates), List.of(note));

        assertThat(view.fields()).containsExactly(name);
        assertThat(view.checks()).containsExactly(dates);
        assertThat(view.notes()).containsExactly(note);
    }

    @Test
    void aFieldFilledInFromTheProfileIsLocked() {
        ViewField unit = new ViewField("unidade", "Divisão de Obras Municipais", true);

        assertThat(unit.locked()).isTrue();
        assertThat(unit.value()).isEqualTo("Divisão de Obras Municipais");
    }

    @Test
    void aViewWithNothingToShowHasEmptyLists() {
        StepView view = new StepView(null, null, null);

        assertThat(view.fields()).isEmpty();
        assertThat(view.checks()).isEmpty();
        assertThat(view.notes()).isEmpty();
    }

    @Test
    void theViewCannotChangeAfterwards() {
        List<ViewField> fields = new ArrayList<>(List.of(new ViewField("nome", "Ana Ferreira", true)));
        StepView view = new StepView(fields, List.of(), List.of());

        fields.clear();

        assertThat(view.fields()).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aFieldOfTheViewHasAnId(String id) {
        assertThatThrownBy(() -> new ViewField(id, "Ana Ferreira", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aCheckHasAName(String name) {
        assertThatThrownBy(() -> new CheckResult(name, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aNoteBelongsToAField(String field) {
        assertThatThrownBy(() -> new FieldNote(field, "O IBAN não corresponde ao seu nome.", RUI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aNoteSaysSomething(String text) {
        assertThatThrownBy(() -> new FieldNote("iban", text, RUI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    void aNoteSaysWhoWroteIt() {
        assertThatThrownBy(() -> new FieldNote("iban", "O IBAN não corresponde ao seu nome.", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("who");
    }
}
