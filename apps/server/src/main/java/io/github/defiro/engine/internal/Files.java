package io.github.defiro.engine.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The files attached to procedures: what each one is in engine.file, its bytes in the
 * {@link FileStore}. Attaching files is UC15 (Dinis); UC17 needs to open them.
 */
@Repository
class Files {

    private final JdbcClient jdbc;
    private final FileStore store;

    Files(JdbcClient jdbc, FileStore store) {
        this.jdbc = jdbc;
        this.store = store;
    }

    /** A file attached to a procedure. The document is the one it came with, if any. */
    record StoredFile(UUID id, UUID procedure, UUID document, String filename, String sha256) {}

    @Transactional
    StoredFile store(UUID procedure, UUID document, String filename, byte[] content) {
        StoredFile file = new StoredFile(UUID.randomUUID(), procedure, document, filename, sha256(content));
        String key = storageKey(file.id());
        jdbc.sql("""
                        insert into engine.file (id, procedure_id, document_id, filename, storage_key, sha256)
                        values (:id, :procedure, :document, :filename, :key, :sha256)""")
                .param("id", file.id())
                .param("procedure", procedure)
                .param("document", document)
                .param("filename", filename)
                .param("key", key)
                .param("sha256", file.sha256())
                .update();
        store.put(key, content);
        return file;
    }

    Optional<StoredFile> find(UUID file) {
        return jdbc.sql("""
                        select id, procedure_id, document_id, filename, sha256
                        from engine.file where id = :id""").param("id", file).query(Files::storedFile).optional();
    }

    /** The files of a procedure, by name. */
    List<StoredFile> of(UUID procedure) {
        return jdbc.sql("""
                        select id, procedure_id, document_id, filename, sha256
                        from engine.file where procedure_id = :procedure order by filename""")
                .param("procedure", procedure)
                .query(Files::storedFile)
                .list();
    }

    private static StoredFile storedFile(ResultSet row, int n) throws SQLException {
        return new StoredFile(
                row.getObject("id", UUID.class),
                row.getObject("procedure_id", UUID.class),
                row.getObject("document_id", UUID.class),
                row.getString("filename"),
                row.getString("sha256"));
    }

    /** The bytes of the file, as they were attached. */
    byte[] content(StoredFile file) {
        return store.get(storageKey(file.id()))
                .orElseThrow(() -> new IllegalStateException("The File store has no bytes for the file " + file.id()));
    }

    // Each file has its own key in the File store: its id.
    private static String storageKey(UUID file) {
        return file.toString();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform has SHA-256", e);
        }
    }
}
