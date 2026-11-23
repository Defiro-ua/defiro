package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StepContextTest {

    static final StepSpecification REVIEW = new StepSpecification(
            "validacao-chefia",
            new PluginRef("review", "0.1.0"),
            "Validação da chefia",
            "chefias",
            5,
            List.of("Incorrectly filled"),
            List.of("Trip not authorised"));
    static final Person ANA = new Person("ana", "Ana Ferreira", "Técnica Superior", "Obras Municipais", "Rui Matos");
    static final Person RUI = new Person("rui", "Rui Matos", "Chefe de Divisão", "Obras Municipais", null);
    final UUID procedure = UUID.randomUUID();

    @Test
    void theContextIsEverythingTheStepNeeds() {
        StepContext context = new StepContext(
                procedure, REVIEW, Map.of("destino", "Lisboa"), ANA, RUI, Map.of("pedido", "submitted"));

        assertThat(context.procedure()).isEqualTo(procedure);
        assertThat(context.step()).isEqualTo(REVIEW);
        assertThat(context.data()).containsEntry("destino", "Lisboa");
        assertThat(context.requester()).isEqualTo(ANA);
        assertThat(context.actingUser()).isEqualTo(RUI);
        assertThat(context.earlierOutcomes()).containsEntry("pedido", "submitted");
    }

    @Test
    void beforeTheFirstSubmissionThereIsNoDataAndNoEarlierOutcome() {
        StepContext context = new StepContext(procedure, REVIEW, null, ANA, ANA, null);

        assertThat(context.data()).isEmpty();
        assertThat(context.earlierOutcomes()).isEmpty();
    }

    @Test
    void anAnswerLeftEmptyIsKept() {
        Map<String, Object> data = new HashMap<>();
        data.put("observacoes", null);

        assertThat(new StepContext(procedure, REVIEW, data, ANA, ANA, null).data())
                .containsEntry("observacoes", null);
    }

    @Test
    void theContextIsReadOnly() {
        Map<String, Object> data = new HashMap<>(Map.of("destino", "Lisboa"));
        StepContext context = new StepContext(procedure, REVIEW, data, ANA, ANA, null);

        data.put("destino", "Porto");

        assertThat(context.data()).containsEntry("destino", "Lisboa");
        assertThatThrownBy(() -> context.data().put("destino", "Porto"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.earlierOutcomes().put("pedido", "submitted"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aStepRunsInsideAProcedure() {
        assertThatThrownBy(() -> new StepContext(null, REVIEW, null, ANA, ANA, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("procedure");
    }

    @Test
    void theContextCarriesTheStepItRuns() {
        assertThatThrownBy(() -> new StepContext(procedure, null, null, ANA, ANA, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step");
    }

    @Test
    void theContextSaysWhoRequestedAndWhoIsActing() {
        assertThatThrownBy(() -> new StepContext(procedure, REVIEW, null, null, ANA, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requester");
        assertThatThrownBy(() -> new StepContext(procedure, REVIEW, null, ANA, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acting");
    }
}
