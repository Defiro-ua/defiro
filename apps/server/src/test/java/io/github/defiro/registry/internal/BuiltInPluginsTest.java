package io.github.defiro.registry.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.registry.PluginDescriptor;
import io.github.defiro.spec.PluginRef;
import org.junit.jupiter.api.Test;

class BuiltInPluginsTest {

    @Test
    void theCatalogueHasTheFourBuiltInPlugins() {
        assertThat(new BuiltInPlugins().plugins())
                .extracting(PluginDescriptor::plugin)
                .containsExactlyInAnyOrder(
                        new PluginRef("submission", "0.1.0"),
                        new PluginRef("review", "0.1.0"),
                        new PluginRef("final-validation", "0.1.0"),
                        new PluginRef("document-generation", "0.1.0"));
    }

    @Test
    void aReviewCanBeApprovedReturnedOrRejected() {
        PluginDescriptor review =
                new BuiltInPlugins().find(new PluginRef("review", "0.1.0")).orElseThrow();

        assertThat(review.outcomes()).containsExactly("approved", "returned", "rejected");
    }

    @Test
    void anUnknownPluginIsNotFound() {
        assertThat(new BuiltInPlugins().find(new PluginRef("review", "9.9.9"))).isEmpty();
        assertThat(new BuiltInPlugins().find(new PluginRef("payment", "0.1.0"))).isEmpty();
    }
}
