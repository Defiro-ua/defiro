package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GeneratedDocumentTest {

    @Test
    void twoDocumentsWithTheSameNameTypeAndContentAreEqual() {
        GeneratedDocument one = new GeneratedDocument("ordem.pdf", "application/pdf", new byte[] {1, 2, 3});
        GeneratedDocument other = new GeneratedDocument("ordem.pdf", "application/pdf", new byte[] {1, 2, 3});

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(one).isNotEqualTo(new GeneratedDocument("ordem.pdf", "application/pdf", new byte[] {9}));
    }

    @Test
    void theContentCannotChangeAfterTheDocumentIsGenerated() {
        byte[] content = {1, 2, 3};
        GeneratedDocument document = new GeneratedDocument("ordem.pdf", "application/pdf", content);

        content[0] = 9;
        document.content()[1] = 9;

        assertThat(document.content()).containsExactly(1, 2, 3);
    }

    @Test
    void itsDescriptionLeavesOutTheContent() {
        assertThat(new GeneratedDocument("ordem.pdf", "application/pdf", new byte[] {1, 2, 3}))
                .hasToString("GeneratedDocument[name=ordem.pdf, mediaType=application/pdf, size=3]");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aDocumentHasAName(String name) {
        assertThatThrownBy(() -> new GeneratedDocument(name, "application/pdf", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aDocumentHasAMediaType(String mediaType) {
        assertThatThrownBy(() -> new GeneratedDocument("ordem.pdf", mediaType, new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("media type");
    }

    @Test
    void aDocumentHasContent() {
        assertThatThrownBy(() -> new GeneratedDocument("ordem.pdf", "application/pdf", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
        assertThatThrownBy(() -> new GeneratedDocument("ordem.pdf", "application/pdf", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }
}
