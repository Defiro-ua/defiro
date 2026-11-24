/**
 * Identity: the people of the organization, their roles and their groups, in the {@code identity}
 * schema. The only module that talks to the Identity Provider, which only says who someone is. The
 * other modules know whoever is signed in by {@code authentication.getName()}: their id in defiro.
 */
@ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {})
package io.github.defiro.identity;

import org.springframework.modulith.ApplicationModule;
