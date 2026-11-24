package io.github.defiro.identity.internal;

import java.util.Set;

/** Whether someone may use defiro, and their roles there. */
record Access(boolean active, Set<String> roles) {

    /** Of someone defiro does not have */
    static final Access NONE = new Access(false, Set.of());

    Access {
        roles = Set.copyOf(roles);
    }
}
