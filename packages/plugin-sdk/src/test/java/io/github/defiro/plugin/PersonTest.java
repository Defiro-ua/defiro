package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PersonTest {

    @Test
    void aPersonIsKnownByTheirIdWithTheirProfile() {
        Person ana = new Person(
                "7d1e0c7a-0000-0000-0000-000000000001",
                "Ana Ferreira",
                "Técnica Superior",
                "Divisão de Obras Municipais",
                "Rui Matos");

        assertThat(ana.id()).isEqualTo("7d1e0c7a-0000-0000-0000-000000000001");
        assertThat(ana.unit()).isEqualTo("Divisão de Obras Municipais");
        assertThat(ana.manager()).isEqualTo("Rui Matos");
    }

    @Test
    void theProfileMayBeIncomplete() {
        Person someone = new Person("7d1e0c7a-0000-0000-0000-000000000002", null, null, null, null);

        assertThat(someone.name()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aPersonHasAnId(String id) {
        assertThatThrownBy(() -> new Person(id, "Ana Ferreira", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }
}
