package io.github.defiro.engine.internal;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * TEMPORARY, for UC15 (Dinis): keeps the bytes in the database table engine.file_content, so UC17
 * can show a request's files before the File store exists. Replace it with the File store's own
 * {@link FileStore}, and drop the table.
 */
@Repository
class TemporaryFileStore implements FileStore {

    private final JdbcClient jdbc;

    TemporaryFileStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void put(String key, byte[] content) {
        jdbc.sql("insert into engine.file_content (storage_key, content) values (:key, :content)")
                .param("key", key)
                .param("content", content)
                .update();
    }

    @Override
    public Optional<byte[]> get(String key) {
        return jdbc.sql("select content from engine.file_content where storage_key = :key")
                .param("key", key)
                .query(byte[].class)
                .optional();
    }
}
