package io.github.defiro.engine.internal;

/** Someone has acted on the step after the approval, so it can no longer be undone. */
class NoLongerUndoableException extends RuntimeException {

    NoLongerUndoableException(String step) {
        super("Já não é possível desfazer: o pedido já foi tratado no passo «" + step + "».");
    }
}
