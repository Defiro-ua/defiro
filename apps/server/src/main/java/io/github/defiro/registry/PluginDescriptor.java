package io.github.defiro.registry;

import io.github.defiro.spec.PluginRef;
import java.util.List;

/**
 * What the catalogue says about a plugin@version.
 *
 * @param outcomes how a step of this plugin can end, for example "approved"
 */
public record PluginDescriptor(PluginRef plugin, List<String> outcomes) {

    public PluginDescriptor {
        outcomes = List.copyOf(outcomes);
    }

    public String name() {
        return plugin.name();
    }

    public String version() {
        return plugin.version();
    }
}
