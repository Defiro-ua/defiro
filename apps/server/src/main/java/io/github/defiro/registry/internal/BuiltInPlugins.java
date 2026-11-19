package io.github.defiro.registry.internal;

import io.github.defiro.registry.PluginCatalogue;
import io.github.defiro.registry.PluginDescriptor;
import io.github.defiro.spec.PluginRef;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The four built-in plugins of {@code plugins/}, at their version there. Until the Registry loads
 * plugin packages, the catalogue is this list.
 */
@Component
class BuiltInPlugins implements PluginCatalogue {

    private static final String VERSION = "0.1.0";
    private static final List<String> DECISIONS = List.of("approved", "returned", "rejected");

    private static final List<PluginDescriptor> PLUGINS = List.of(
            new PluginDescriptor(new PluginRef("submission", VERSION), List.of("submitted")),
            new PluginDescriptor(new PluginRef("review", VERSION), DECISIONS),
            new PluginDescriptor(new PluginRef("final-validation", VERSION), DECISIONS),
            new PluginDescriptor(new PluginRef("document-generation", VERSION), List.of("generated")));

    @Override
    public List<PluginDescriptor> plugins() {
        return PLUGINS;
    }

    @Override
    public Optional<PluginDescriptor> find(PluginRef plugin) {
        return PLUGINS.stream().filter(each -> each.plugin().equals(plugin)).findFirst();
    }
}
