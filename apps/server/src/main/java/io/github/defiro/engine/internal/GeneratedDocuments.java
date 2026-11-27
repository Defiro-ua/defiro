package io.github.defiro.engine.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The final document of a closed procedure: what it is in engine.generated_document, its bytes in
 * the {@link FileStore}. A procedure has at most one. UC22 generates it, and UC23 downloads it.
 */
@Repository
class GeneratedDocuments {

    private final JdbcClient jdbc;
    private final FileStore store;

    GeneratedDocuments(JdbcClient jdbc, FileStore store) {
        this.jdbc = jdbc;
        this.store = store;
    }

    /** The final document of a procedure. */
    record Document(UUID procedure, String filename, String mediaType, String sha256) {}

    /** Keeps the document of the procedure. A second one for the same procedure is a DuplicateKeyException. */
    @Transactional
    Document store(UUID procedure, String filename, String mediaType, byte[] content) {
        Document document = new Document(procedure, filename, mediaType, Digests.sha256(content));
        String key = storageKey(procedure);
        jdbc.sql("""
                        insert into engine.generated_document (procedure_id, filename, media_type, storage_key, sha256)
                        values (:procedure, :filename, :mediaType, :key, :sha256)""")
                .param("procedure", procedure)
                .param("filename", filename)
                .param("mediaType", mediaType)
                .param("key", key)
                .param("sha256", document.sha256())
                .update();
        store.put(key, content);
        return document;
    }

    /** The document of the procedure, once it has one. */
    Optional<Document> of(UUID procedure) {
        return jdbc.sql("""
                        select procedure_id, filename, media_type, sha256
                        from engine.generated_document where procedure_id = :procedure""")
                .param("procedure", procedure)
                .query((row, n) -> new Document(
                        row.getObject("procedure_id", UUID.class),
                        row.getString("filename"),
                        row.getString("media_type"),
                        row.getString("sha256")))
                .optional();
    }

    /** The bytes of the document, as they were generated. */
    byte[] content(Document document) {
        return store.get(storageKey(document.procedure()))
                .orElseThrow(() -> new IllegalStateException(
                        "The File store has no bytes for the document of the procedure " + document.procedure()));
    }

    // One document per procedure, so its key in the File store is the procedure's, apart from the files'.
    private static String storageKey(UUID procedure) {
        return "generated/" + procedure;
    }
}
