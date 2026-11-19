package io.github.defiro.designer.internal;

import io.github.defiro.registry.PluginCatalogue;
import io.github.defiro.registry.PluginDescriptor;
import io.github.defiro.spec.PluginRef;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Publishing a draft: load it, check it against every workflow rule and the plugins in the
 * catalogue, build the specification, and keep it as the workflow's next version. Safe to retry:
 * the same revision of the draft gives back the version it already has. The Engine starts new
 * procedures on the latest version, through {@code designer :: workflows}.
 */
@Service
class Publisher {

    private final Drafts drafts;
    private final PluginCatalogue catalogue;
    private final WorkflowVersions versions;

    Publisher(Drafts drafts, PluginCatalogue catalogue, WorkflowVersions versions) {
        this.drafts = drafts;
        this.catalogue = catalogue;
        this.versions = versions;
    }

    /**
     * Who publishes: the stable id of their account, and their name as it is now.
     *
     * @param id the subject of their token
     */
    record Who(String id, String name) {}

    /** The version it became, or empty if there is no such draft. */
    Optional<Integer> publish(UUID id, Who who) {
        return drafts.find(id).map(stored -> {
            WorkflowDraft draft = stored.draft();
            Set<PluginRef> plugins =
                    catalogue.plugins().stream().map(PluginDescriptor::plugin).collect(Collectors.toSet());
            List<Problem> problems = draft.problems(plugins);
            if (!problems.isEmpty()) {
                throw new IncompleteWorkflowException(problems);
            }
            return versions.add(
                    id,
                    stored.revision(),
                    draft.specification(
                            plugin -> catalogue.find(plugin).orElseThrow().outcomes()),
                    who);
        });
    }
}
