package io.github.defiro.plugin;

import java.util.Arrays;
import java.util.Objects;

/**
 * A document a step generated, for example the reimbursement order.
 *
 * @param mediaType for example {@code application/pdf}
 * @param content the document's bytes; each call gives a new copy
 */
public record GeneratedDocument(String name, String mediaType, byte[] content) {

    public GeneratedDocument {
        Require.text(name, "A generated document needs a name");
        Require.text(mediaType, "A generated document needs its media type");
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("A generated document needs content");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GeneratedDocument document
                && name.equals(document.name)
                && mediaType.equals(document.mediaType)
                && Arrays.equals(content, document.content);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(name, mediaType) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "GeneratedDocument[name=" + name + ", mediaType=" + mediaType + ", size=" + content.length + "]";
    }
}
