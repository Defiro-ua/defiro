package io.github.defiro.designer.internal;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Who can make a request and who can handle a step, as the designer offers them: the
 * organization's groups, after the one that defiro works out itself.
 */
@Component
class GroupOptions {

    static final Option EVERYONE = new Option("Todos os trabalhadores", "Todas as pessoas da organização");
    static final Option REQUESTERS_MANAGERS =
            new Option("Chefias da unidade do requerente", "Calculado a partir da unidade de quem faz o pedido");

    private static final String NOTE = "Grupo da organização";

    private final OrganizationGroups groups;

    GroupOptions(OrganizationGroups groups) {
        this.groups = groups;
    }

    /** An option, with a note on where it comes from */
    record Option(String name, String note) {}

    List<Option> requesters() {
        return Stream.concat(Stream.of(EVERYONE), organization()).toList();
    }

    List<Option> handlers() {
        return Stream.concat(Stream.of(REQUESTERS_MANAGERS), organization()).toList();
    }

    Groups groups() {
        return new Groups(names(requesters()), names(handlers()));
    }

    private Stream<Option> organization() {
        return groups.names().stream().map(name -> new Option(name, NOTE));
    }

    private static List<String> names(List<Option> options) {
        return options.stream().map(Option::name).toList();
    }
}
