/**
 * Workflow Designer: drafts, validating workflows against every rule, publishing specifications.
 * May use only the Engine's Deployment interface, the Plugin Registry and the Directory of Identity.
 */
@ApplicationModule(
        displayName = "Workflow Designer",
        allowedDependencies = {"engine :: deployment", "registry", "identity :: directory"})
package io.github.defiro.designer;

import org.springframework.modulith.ApplicationModule;
