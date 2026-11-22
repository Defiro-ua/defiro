package io.github.defiro.engine.internal;

import io.github.defiro.identity.Directory;
import io.github.defiro.identity.Person;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Who may handle a step: the members of the group or the unit of Entra ID that handles it, as the
 * Designer set it. The first of them to act decides the step.
 *
 * <p>«Chefias da unidade do requerente» is not a group: it is the members of «Chefias» in the
 * requester's unit. Any other name is a group when the directory knows it (UC05, see
 * {@link Directory}), and otherwise a unit, which the token names in {@code department}.
 */
@Component
class Candidates {

    static final String HEADS_OF_THE_REQUESTERS_UNIT = "Chefias da unidade do requerente";
    static final String HEADS = "Chefias";

    private final Directory directory;

    Candidates(Directory directory) {
        this.directory = directory;
    }

    /** What a task of the step keeps as its group: the Entra ID group's id, or the unit's name. */
    String groupOf(String handledBy) {
        String group = HEADS_OF_THE_REQUESTERS_UNIT.equals(handledBy) ? HEADS : handledBy;
        return directory.groupId(group).orElse(handledBy);
    }

    boolean canHandle(String handledBy, String requesterUnit, Person person) {
        if (HEADS_OF_THE_REQUESTERS_UNIT.equals(handledBy)) {
            return inGroup(HEADS, person)
                    && person.unit() != null
                    && person.unit().equals(requesterUnit);
        }
        return directory
                .groupId(handledBy)
                .map(id -> person.groups().contains(id))
                .orElseGet(() -> Objects.equals(handledBy, person.unit()));
    }

    private boolean inGroup(String name, Person person) {
        return directory.groupId(name).map(person.groups()::contains).orElse(false);
    }
}
