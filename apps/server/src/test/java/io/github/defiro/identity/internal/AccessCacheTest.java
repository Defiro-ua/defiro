package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AccessCacheTest {

    private static final UUID ID = UUID.fromString("0f9d3c52-8a51-4f0e-b7c1-2d5e6a7b8c90");
    private static final UUID SOMEONE_ELSE = UUID.fromString("7b1e2c34-5d6f-4a7b-8c9d-0e1f2a3b4c5d");

    private final People people = mock(People.class);
    private final AtomicLong nanoseconds = new AtomicLong();
    private final AccessCache cache = new AccessCache(people, nanoseconds::get, Duration.ofSeconds(60));

    @Test
    void withinAMinuteTheDatabaseIsAskedOnce() {
        when(people.access(ID)).thenReturn(new Access(true, Set.of("WORKFLOW_MANAGER")));

        assertThat(cache.of(ID).roles()).containsExactly("WORKFLOW_MANAGER");
        after(Duration.ofSeconds(59));
        assertThat(cache.of(ID).roles()).containsExactly("WORKFLOW_MANAGER");

        verify(people, times(1)).access(ID);
    }

    @Test
    void afterAMinuteAWithdrawnRoleIsGone() {
        when(people.access(ID)).thenReturn(new Access(true, Set.of("WORKFLOW_MANAGER")), new Access(true, Set.of()));

        assertThat(cache.of(ID).roles()).containsExactly("WORKFLOW_MANAGER");
        after(Duration.ofSeconds(61));

        assertThat(cache.of(ID).roles()).isEmpty();
    }

    @Test
    void aMinuteCountsFromWhenItWasReadAndNotFromWhenItWasLastUsed() {
        when(people.access(ID)).thenReturn(new Access(true, Set.of("WORKFLOW_MANAGER")), new Access(true, Set.of()));

        cache.of(ID);
        after(Duration.ofSeconds(40));
        cache.of(ID);
        after(Duration.ofSeconds(21));

        assertThat(cache.of(ID).roles()).isEmpty();
    }

    @Test
    void eachPersonHasTheirOwnEntry() {
        when(people.access(ID)).thenReturn(new Access(true, Set.of("WORKFLOW_MANAGER")));
        when(people.access(SOMEONE_ELSE)).thenReturn(new Access(true, Set.of()));

        assertThat(cache.of(ID).roles()).containsExactly("WORKFLOW_MANAGER");
        assertThat(cache.of(SOMEONE_ELSE).roles()).isEmpty();
    }

    private void after(Duration time) {
        nanoseconds.addAndGet(time.toNanos());
    }
}
