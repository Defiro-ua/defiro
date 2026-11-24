package io.github.defiro.identity.internal;

import java.util.List;
import java.util.Set;

/**
 * Who is signed in, as defiro knows them. The groups are the names of those they belong to; the menu
 * lists the web app's side menu items they may see.
 */
record Profile(String name, String unit, String jobTitle, String manager, List<String> groups, List<String> menu) {

    private static final String WORKFLOW_MANAGER = "WORKFLOW_MANAGER";

    private static final List<String> EVERYONE = List.of("new-request", "inbox");
    private static final List<String> WORKFLOW_MANAGERS =
            List.of("create-workflow", "new-request", "inbox", "workflows");

    static List<String> menuFor(Set<String> roles) {
        return roles.contains(WORKFLOW_MANAGER) ? WORKFLOW_MANAGERS : EVERYONE;
    }
}
