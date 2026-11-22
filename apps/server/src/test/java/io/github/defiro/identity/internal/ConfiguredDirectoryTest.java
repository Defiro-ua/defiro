package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ConfiguredDirectoryTest {

    @Test
    void aConfiguredGroupHasItsId() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("defiro.identity.groups[0].name", "Chefias")
                .withProperty("defiro.identity.groups[0].id", "8f1c2a5e-0000-4000-8000-000000000001")
                .withProperty("defiro.identity.groups[1].name", "RH · Formação")
                .withProperty("defiro.identity.groups[1].id", "8f1c2a5e-0000-4000-8000-000000000002");

        ConfiguredDirectory directory = new ConfiguredDirectory(environment);

        assertThat(directory.groupId("Chefias")).contains("8f1c2a5e-0000-4000-8000-000000000001");
        assertThat(directory.groupId("RH · Formação")).contains("8f1c2a5e-0000-4000-8000-000000000002");
    }

    @Test
    void aNameThatIsNotAConfiguredGroupHasNoId() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("defiro.identity.groups[0].name", "Chefias")
                .withProperty("defiro.identity.groups[0].id", "8f1c2a5e-0000-4000-8000-000000000001");

        assertThat(new ConfiguredDirectory(environment).groupId("Divisão Financeira"))
                .isEmpty();
    }

    @Test
    void withNoGroupsConfiguredNoNameHasAnId() {
        assertThat(new ConfiguredDirectory(new MockEnvironment()).groupId("Chefias"))
                .isEmpty();
    }
}
