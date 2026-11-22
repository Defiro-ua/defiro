package io.github.defiro.engine.internal;

import io.github.defiro.identity.Person;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The reviewer's note on a field of the request, on the task they took (UC19). */
@RestController
@RequestMapping("/api/engine/tasks/{task}/notes/{field}")
class NoteController {

    private final NoteEditor editor;

    NoteController(NoteEditor editor) {
        this.editor = editor;
    }

    record Text(String text) {}

    @PutMapping
    ResponseEntity<Notes.Note> put(
            @PathVariable UUID task,
            @PathVariable String field,
            @RequestBody Text body,
            @AuthenticationPrincipal ClaimAccessor user) {
        return ResponseEntity.of(editor.put(task, field, body.text(), Person.of(user)));
    }

    @DeleteMapping
    ResponseEntity<Void> delete(
            @PathVariable UUID task, @PathVariable String field, @AuthenticationPrincipal ClaimAccessor user) {
        return editor.delete(task, field, Person.of(user))
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** 422: empty, too long, or on no field of the request. The web app shows its own texts. */
    @ExceptionHandler
    ProblemDetail invalid(InvalidNoteException invalid) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, invalid.getMessage());
    }
}
