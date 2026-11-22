package io.github.defiro.engine.internal;

/** The note is empty, longer than Notes.LONGEST, or on no field of the request. */
class InvalidNoteException extends RuntimeException {

    InvalidNoteException(String why) {
        super(why);
    }
}
