package io.github.defiro.plugin;

import java.util.List;

/**
 * What a plugin is, as the Plugin Registry lists it and the Workflow Designer checks a workflow
 * against it.
 *
 * @param configFields what a step of this plugin must be configured with, for example "assignee"
 * @param outcomes how a step of this plugin can end, for example "approved": each needs a transition
 */
public record Descriptor(String name, String version, List<String> configFields, List<String> outcomes) {

    public Descriptor {
        Require.text(name, "A descriptor needs the plugin's name");
        Require.text(version, "A descriptor needs the plugin's version");
        configFields = Require.copy(configFields);
        outcomes = Require.copy(outcomes);
        if (outcomes.isEmpty()) {
            throw new IllegalArgumentException("A plugin ends with at least one outcome");
        }
    }
}
