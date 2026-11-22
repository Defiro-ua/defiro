package io.github.defiro.engine.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The person signed in takes no part in the request, or may not do this to it. */
@ResponseStatus(HttpStatus.FORBIDDEN)
class NotAllowedException extends RuntimeException {

    NotAllowedException() {
        super("This person may not do this to the request");
    }
}
