package io.github.defiro.designer.internal;

/** A missing or invalid answer in a draft: the field it belongs to, and what to tell the Workflow Manager. */
record Problem(String field, String message) {}
