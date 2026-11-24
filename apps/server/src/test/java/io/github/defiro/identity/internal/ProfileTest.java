package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfileTest {

    @Test
    void aWorkflowManagerAlsoGetsTheWorkflowsInTheMenu() {
        assertThat(Profile.menuFor(Set.of("WORKFLOW_MANAGER")))
                .containsExactly("create-workflow", "new-request", "inbox", "workflows");
    }

    @Test
    void anEmployeeWithoutTheRoleGetsOnlyRequestsAndTheInbox() {
        assertThat(Profile.menuFor(Set.of())).containsExactly("new-request", "inbox");
    }

    @Test
    void anAdministratorAloneDoesNotGetTheWorkflows() {
        assertThat(Profile.menuFor(Set.of("ADMINISTRATOR"))).containsExactly("new-request", "inbox");
    }

    @Test
    void anAdministratorWhoIsAlsoAWorkflowManagerGetsTheWorkflows() {
        assertThat(Profile.menuFor(Set.of("ADMINISTRATOR", "WORKFLOW_MANAGER"))).contains("workflows");
    }
}
