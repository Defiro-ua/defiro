/**
 * Workflow Designer: drafts, validating workflows against every rule, and the published versions
 * of each workflow. The Engine reads the version in force through {@code designer :: workflows}.
 * May use only the Plugin Registry.
 */
@ApplicationModule(
        displayName = "Workflow Designer",
        allowedDependencies = {"registry"})
package io.github.defiro.designer;

import org.springframework.modulith.ApplicationModule;
