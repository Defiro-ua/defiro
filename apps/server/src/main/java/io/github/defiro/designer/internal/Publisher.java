package io.github.defiro.designer.internal;

import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
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
 * Publishing a draft, as docs/sequence_diagram.txt describes it: load the draft, check it against
 * every workflow rule and the plugins in the catalogue, build the specification, deploy it to the
 * Engine, and mark the draft as published with the version the Engine gives back. Safe to retry:
 * the Engine gives the same version for the same revision.
 */
@Service
class Publisher {

    private final Drafts drafts;
    private final PluginCatalogue catalogue;
    private final Deployment deployment;

    Publisher(Drafts drafts, PluginCatalogue catalogue, Deployment deployment) {
        this.drafts = drafts;
        this.catalogue = catalogue;
        this.deployment = deployment;
    }

    /**
     * Who publishes: the stable id of their account, and their name as it is now.
     *
     * @param id the subject of their token, which the Engine records as publishedBy
     */
    record Who(String id, String name) {}

    /** The version the Engine gave, or empty if there is no such draft. */
    Optional<Integer> publish(UUID id, Who who) {
        return drafts.find(id).map(stored -> {
            WorkflowDraft draft = stored.draft();
            Set<PluginRef> plugins =
                    catalogue.plugins().stream().map(PluginDescriptor::plugin).collect(Collectors.toSet());
            List<Problem> problems = draft.problems(plugins);
            if (!problems.isEmpty()) {
                throw new IncompleteWorkflowException(problems);
            }
            int version = deployment.deploy(
                    draft.specification(
                            plugin -> catalogue.find(plugin).orElseThrow().outcomes()),
                    new Publication(who.id(), id, stored.revision()));
            drafts.markPublished(id, stored.revision(), version, who);
            return version;
        });
    }
}
