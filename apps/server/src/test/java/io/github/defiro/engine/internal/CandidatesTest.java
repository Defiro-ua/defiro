package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.identity.Person;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CandidatesTest {

    static final String HEADS = "id-chefias";
    static final String TRAINING = "id-rh-formacao";

    final Candidates candidates = new Candidates(name -> Optional.ofNullable(
            Map.of("Chefias", HEADS, "RH · Formação", TRAINING).get(name)));

    @Test
    void aHeadOfTheRequestersUnitIsACandidate() {
        Person rui = person("Divisão de Obras Municipais", HEADS);

        assertThat(candidates.canHandle("Chefias da unidade do requerente", "Divisão de Obras Municipais", rui))
                .isTrue();
    }

    @Test
    void aHeadOfAnotherUnitIsNot() {
        Person luisa = person("Divisão de Ambiente", HEADS);

        assertThat(candidates.canHandle("Chefias da unidade do requerente", "Divisão de Obras Municipais", luisa))
                .isFalse();
    }

    @Test
    void someoneOfTheRequestersUnitWhoIsNotAHeadIsNot() {
        Person ana = person("Divisão de Obras Municipais");

        assertThat(candidates.canHandle("Chefias da unidade do requerente", "Divisão de Obras Municipais", ana))
                .isFalse();
    }

    @Test
    void aMemberOfTheGroupIsACandidate() {
        assertThat(candidates.canHandle(
                        "RH · Formação", "Divisão de Obras Municipais", person("Recursos Humanos", TRAINING)))
                .isTrue();
    }

    @Test
    void someoneOutsideTheGroupIsNot() {
        assertThat(candidates.canHandle(
                        "RH · Formação", "Divisão de Obras Municipais", person("Recursos Humanos", HEADS)))
                .isFalse();
    }

    @Test
    void aStepHandledByAUnitHasTheUnitsPeopleAsCandidates() {
        assertThat(candidates.canHandle(
                        "Divisão Financeira", "Divisão de Obras Municipais", person("Divisão Financeira")))
                .isTrue();
        assertThat(candidates.canHandle(
                        "Divisão Financeira", "Divisão de Obras Municipais", person("Divisão de Obras Municipais")))
                .isFalse();
    }

    @Test
    void aTaskKeepsTheGroupsIdOrTheUnitsName() {
        assertThat(candidates.groupOf("Chefias da unidade do requerente")).isEqualTo(HEADS);
        assertThat(candidates.groupOf("RH · Formação")).isEqualTo(TRAINING);
        assertThat(candidates.groupOf("Divisão Financeira")).isEqualTo("Divisão Financeira");
    }

    private static Person person(String unit, String... groups) {
        return new Person("someone", "Someone", "Técnico", unit, List.of(groups));
    }
}
