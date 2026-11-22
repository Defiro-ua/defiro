package io.github.defiro.engine.internal;

import java.util.Optional;

/**
 * The File store of the architecture: it keeps the bytes of each file, by a key of its own. What
 * the file is, and whose, is in {@link Files}.
 *
 * <p>UC15 (Dinis) builds the real one. Until then {@link TemporaryFileStore} stands in for it.
 */
interface FileStore {

    void put(String key, byte[] content);

    Optional<byte[]> get(String key);
}
