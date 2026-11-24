package io.github.defiro.identity.internal;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.UUID;

/**
 * Each person's access, read from the database and kept for a short time: a withdrawn role or a
 * deactivated person counts for that long at most.
 */
class AccessCache {

    private static final int PEOPLE = 10_000;

    private final LoadingCache<UUID, Access> cache;

    AccessCache(People people, Ticker ticker, Duration ttl) {
        this.cache = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(ttl)
                .maximumSize(PEOPLE)
                .build(people::access);
    }

    Access of(UUID person) {
        return cache.get(person);
    }
}
