package io.github.defiro.designer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.defiro.designer.internal.GroupOptions.Option;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupOptionsTest {

    private final OrganizationGroups groups = mock(OrganizationGroups.class);
    private final GroupOptions options = new GroupOptions(groups);

    @BeforeEach
    void theOrganizationHasTwoGroups() {
        when(groups.names()).thenReturn(List.of("Chefias", "Divisão Financeira"));
    }

    @Test
    void everyEmployeeOrAGroupOrUnitOfTheOrganizationCanBeWhoRequests() {
        assertThat(options.requesters())
                .containsExactly(
                        new Option("Todos os trabalhadores", "Todas as pessoas da organização"),
                        new Option("Chefias", "Grupo da organização"),
                        new Option("Divisão Financeira", "Grupo da organização"));
    }

    @Test
    void theRequestersManagersOrAGroupOrUnitOfTheOrganizationHandleAStep() {
        assertThat(options.handlers())
                .containsExactly(
                        new Option(
                                "Chefias da unidade do requerente",
                                "Calculado a partir da unidade de quem faz o pedido"),
                        new Option("Chefias", "Grupo da organização"),
                        new Option("Divisão Financeira", "Grupo da organização"));
    }

    @Test
    void theRulesCheckTheNamesOfTheseOptions() {
        assertThat(options.groups())
                .isEqualTo(new Groups(
                        List.of("Todos os trabalhadores", "Chefias", "Divisão Financeira"),
                        List.of("Chefias da unidade do requerente", "Chefias", "Divisão Financeira")));
    }
}
