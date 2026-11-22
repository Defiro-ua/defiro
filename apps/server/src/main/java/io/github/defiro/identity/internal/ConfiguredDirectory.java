package io.github.defiro.identity.internal;

import io.github.defiro.identity.directory.Directory;
import io.github.defiro.identity.directory.Group;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The directory as the configuration lists it, in {@code defiro.directory.groups}: each with its
 * {@code name} and {@code kind} ({@code group} or {@code unit}). As agreed for UC05, it stands in for
 * Entra ID until defiro reads the groups through Microsoft Graph.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfiguredDirectory.Groups.class)
class ConfiguredDirectory implements Directory {

    @ConfigurationProperties("defiro.directory")
    record Groups(List<Group> groups) {

        Groups {
            groups = groups == null ? List.of() : List.copyOf(groups);
        }
    }

    private final Groups configured;

    ConfiguredDirectory(Groups configured) {
        this.configured = configured;
    }

    @Override
    public List<Group> groupsAndUnits() {
        return configured.groups();
    }
}
