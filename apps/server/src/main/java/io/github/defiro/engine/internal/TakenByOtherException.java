package io.github.defiro.engine.internal;

/** Another candidate took the task first: the request is in review with them. */
class TakenByOtherException extends RuntimeException {

    TakenByOtherException(String reviewer) {
        super("Em revisão por " + reviewer + ".");
    }
}
