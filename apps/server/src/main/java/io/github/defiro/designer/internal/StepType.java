package io.github.defiro.designer.internal;

enum StepType {
    /** The first step: someone fills in the request. */
    REQUEST,
    /** Someone checks the request, and may pass it on, return it or reject it. */
    REVIEW,
    /** The last check before the request is done. There is at most one, and it is always last. */
    FINAL_VALIDATION
}
