package io.github.defiro.registry;

import io.github.defiro.spec.PluginRef;
import java.util.List;
import java.util.Optional;

/** The plugins the Registry holds, at every version: the step types a workflow can use. */
public interface PluginCatalogue {

    List<PluginDescriptor> plugins();

    Optional<PluginDescriptor> find(PluginRef plugin);
}
