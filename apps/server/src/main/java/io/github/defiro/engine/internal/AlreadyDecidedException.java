package io.github.defiro.engine.internal;

/** Someone else decided the task first: the first to act wins. */
class AlreadyDecidedException extends RuntimeException {

    AlreadyDecidedException(String name, String jobTitle) {
        super("Este pedido já foi tratado por " + name + ", " + jobTitle + ".");
    }
}
