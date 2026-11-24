package io.github.defiro.identity.internal;

/**
 * What the identity provider says about whoever signs in: who they are, and nothing about what they
 * may do.
 *
 * @param name may be missing
 * @param email may be missing
 */
record IdpAccount(String issuer, String subject, String name, String email, boolean emailVerified) {}
