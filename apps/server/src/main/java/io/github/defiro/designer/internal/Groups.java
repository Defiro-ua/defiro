package io.github.defiro.designer.internal;

import java.util.List;

/**
 * The names a draft may give to who makes the request and to who handles each step (UC05).
 *
 * @param requesters who can make the request: everyone, or a group or unit of the organization
 * @param handlers who can handle a step: the requester's managers, or a group or unit
 */
record Groups(List<String> requesters, List<String> handlers) {

    Groups {
        requesters = List.copyOf(requesters);
        handlers = List.copyOf(handlers);
    }
}
