package io.github.defiro.designer.internal;

/** A reason a step can give for returning or rejecting a request. */
record Reason(String name, Outcome outcome) {}
