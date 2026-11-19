/**
 * Workflow Engine: runs procedure instances and hosts plugins. Starts each procedure on the
 * version of the workflow in force, which it reads from the Designer, and keeps a copy of it.
 */
@ApplicationModule(
        displayName = "Workflow Engine",
        allowedDependencies = {"registry", "identity", "designer :: workflows"})
package io.github.defiro.engine;

import org.springframework.modulith.ApplicationModule;
