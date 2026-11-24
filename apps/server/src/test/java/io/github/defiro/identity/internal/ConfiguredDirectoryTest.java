package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.identity.directory.Directory;
import io.github.defiro.identity.directory.Group;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// Until defiro reads them from Entra ID (Microsoft Graph), the groups and units are configured.
class ConfiguredDirectoryTest {

    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(ConfiguredDirectory.class);

    @Test
    void theGroupsAndUnitsAreTheConfiguredOnesInOrder() {
        context.withPropertyValues(
                        "defiro.directory.groups[0].name=Divisão Financeira",
                        "defiro.directory.groups[0].kind=unit",
                        "defiro.directory.groups[1].name=Chefias",
                        "defiro.directory.groups[1].kind=group")
                .run(app -> assertThat(app.getBean(Directory.class).groupsAndUnits())
                        .containsExactly(
                                new Group("Divisão Financeira", Group.Kind.UNIT),
                                new Group("Chefias", Group.Kind.GROUP)));
    }

    @Test
    void anOrganizationWithNoneConfiguredHasNone() {
        context.run(
                app -> assertThat(app.getBean(Directory.class).groupsAndUnits()).isEmpty());
    }
}
