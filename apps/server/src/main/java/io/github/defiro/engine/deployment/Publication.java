package io.github.defiro.engine.deployment;

import java.util.UUID;

/**
 * Who published which draft, and at which of its revisions.
 *
 * @param draft the Designer's draft: it names the workflow across its versions
 * @param revision the draft's revision that was validated and published
 */
public record Publication(String publishedBy, UUID draft, int revision) {}
