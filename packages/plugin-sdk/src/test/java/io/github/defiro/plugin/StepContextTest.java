package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import java.util.List;
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

    @Test
    void theContextIsTheProcedureAndItsStep() {
        UUID procedure = UUID.randomUUID();

        StepContext context = new StepContext(procedure, REVIEW);

        assertThat(context.procedure()).isEqualTo(procedure);
        assertThat(context.step()).isEqualTo(REVIEW);
    }

    @Test
    void aStepRunsInsideAProcedure() {
        assertThatThrownBy(() -> new StepContext(null, REVIEW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("procedure");
    }

    @Test
    void theContextCarriesTheStepItRuns() {
        UUID procedure = UUID.randomUUID();

        assertThatThrownBy(() -> new StepContext(procedure, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step");
    }
}
