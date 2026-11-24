/**
 * Workflow Engine: runs procedure instances and hosts plugins.
 */
@ApplicationModule(
        displayName = "Workflow Engine",
        allowedDependencies = {"registry"})
package io.github.defiro.engine;

import org.springframework.modulith.ApplicationModule;
