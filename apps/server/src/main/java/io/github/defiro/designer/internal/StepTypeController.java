package io.github.defiro.designer.internal;

import io.github.defiro.registry.PluginCatalogue;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The step palette: the plugins of the Registry's catalogue, each a type of step. */
// Only a Workflow Manager designs workflows: the app role that identity turns into a role.
@PreAuthorize("hasRole('WorkflowManager')")
@RestController
class StepTypeController {

    private final PluginCatalogue catalogue;

    StepTypeController(PluginCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    record StepType(String name, String version, List<String> outcomes) {}

    @GetMapping("/api/designer/step-types")
    List<StepType> palette() {
        return catalogue.plugins().stream()
                .map(plugin -> new StepType(plugin.name(), plugin.version(), plugin.outcomes()))
                .toList();
    }
}
