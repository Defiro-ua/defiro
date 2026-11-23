package io.github.defiro.engine.internal;

/** The return or rejection has no reason, one that is not the step's, or not the message it needs. */
class InvalidDecisionException extends RuntimeException {

    InvalidDecisionException(String why) {
        super(why);
    }
}
