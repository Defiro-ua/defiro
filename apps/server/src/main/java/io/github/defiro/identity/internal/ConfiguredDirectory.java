package io.github.defiro.identity.internal;

import io.github.defiro.identity.Directory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * TEMPORARY, for UC05 (Candeias): the groups' names and ids come from the configuration, as
 * {@code defiro.identity.groups[0].name} and {@code defiro.identity.groups[0].id}, one pair per
 * group. With none set, no step handled by a group has candidates. The real directory would ask
 * Microsoft Graph, but UC05 should rather keep the group's id in the specification.
 */
@Component
class ConfiguredDirectory implements Directory {

    record Group(String name, String id) {}

    private final Map<String, String> ids;

    ConfiguredDirectory(Environment environment) {
        List<Group> groups = Binder.get(environment)
                .bind("defiro.identity.groups", Bindable.listOf(Group.class))
                .orElse(List.of());
        this.ids = groups.stream().collect(Collectors.toUnmodifiableMap(Group::name, Group::id));
    }

    @Override
    public Optional<String> groupId(String name) {
        return Optional.ofNullable(ids.get(name));
    }
}
