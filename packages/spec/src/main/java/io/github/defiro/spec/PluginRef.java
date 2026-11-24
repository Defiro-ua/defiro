package io.github.defiro.spec;

/** A plugin at one version, written {@code name@version}: what a step of a workflow runs. */
public record PluginRef(String name, String version) {

    @Override
    public String toString() {
        return name + "@" + version;
    }
}
