package io.github.defiro.identity.internal;

/** The person exists in defiro, and is no longer active there. */
class InactiveAccountException extends RuntimeException {

    InactiveAccountException() {
        super("This person is inactive in defiro");
    }
}
