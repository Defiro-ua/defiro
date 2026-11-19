package io.github.defiro.designer.internal;

import java.time.Instant;

/**
 * The last time a draft was published: what «Workflows» shows as «Versão N · criado por … a …».
 *
 * @param version the version the Engine gave
 * @param by who published it, by the stable id of their account
 * @param byName their name when they published it
 */
record Published(int version, String by, String byName, Instant at) {}
