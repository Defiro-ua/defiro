package io.github.defiro.designer.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * The last time a draft was published: what «Workflows» shows as «Versão N · criado por … a …».
 *
 * @param version the version the Engine gave
 * @param by who published it, by their id in defiro
 * @param byName their name when they published it
 */
record Published(int version, UUID by, String byName, Instant at) {}
