package io.github.defiro.designer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.designer.internal.GroupOptions.Option;
import io.github.defiro.identity.directory.Group;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupOptionsTest {

    private final GroupOptions options = new GroupOptions(
            () -> List.of(new Group("Chefias", Group.Kind.GROUP), new Group("Divisão Financeira", Group.Kind.UNIT)));

    @Test
    void everyEmployeeOrAGroupOrUnitOfTheOrganizationCanBeWhoRequests() {
        assertThat(options.requesters())
                .containsExactly(
                        new Option("Todos os trabalhadores", "Todas as pessoas da organização"),
                        new Option("Chefias", "Grupo do IdP"),
                        new Option("Divisão Financeira", "Unidade do IdP"));
    }

    @Test
    void theRequestersManagersOrAGroupOrUnitOfTheOrganizationHandleAStep() {
        assertThat(options.handlers())
                .containsExactly(
                        new Option(
                                "Chefias da unidade do requerente",
                                "Calculado a partir da unidade de quem faz o pedido"),
                        new Option("Chefias", "Grupo do IdP"),
                        new Option("Divisão Financeira", "Unidade do IdP"));
    }

    @Test
    void theRulesCheckTheNamesOfTheseOptions() {
        assertThat(options.groups())
                .isEqualTo(new Groups(
                        List.of("Todos os trabalhadores", "Chefias", "Divisão Financeira"),
                        List.of("Chefias da unidade do requerente", "Chefias", "Divisão Financeira")));
    }
}
