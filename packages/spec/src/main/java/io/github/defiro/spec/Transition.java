package io.github.defiro.spec;

/**
 * Where a request goes when a step ends with an outcome, for example "approved".
 *
 * @param to the id of the next step, or null when the procedure ends
 */
public record Transition(String from, String outcome, String to) {}
