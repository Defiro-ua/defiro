package io.github.defiro.engine.internal;

import io.github.defiro.identity.Person;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reviewer's decisions, on the task they opened: a decision is about one step, so someone who
 * comes late is told who decided it, and never decides the next step by mistake.
 */
@RestController
@RequestMapping("/api/engine/tasks/{id}")
class TaskController {

    private final Decisions decisions;

    TaskController(Decisions decisions) {
        this.decisions = decisions;
    }

    /** «Rever» in the inbox: the candidate takes the task, so nobody else reviews it meanwhile. */
    @PostMapping("/take")
    ResponseEntity<Decisions.Taken> take(@PathVariable UUID id, @AuthenticationPrincipal ClaimAccessor user) {
        return ResponseEntity.of(decisions.take(id, Person.of(user)));
    }

    /** «Libertar a tarefa»: back to every candidate. */
    @PostMapping("/release")
    ResponseEntity<Decisions.Taken> release(@PathVariable UUID id, @AuthenticationPrincipal ClaimAccessor user) {
        return ResponseEntity.of(decisions.release(id, Person.of(user)));
    }

    @PostMapping("/approve")
    ResponseEntity<Decisions.Approved> approve(@PathVariable UUID id, @AuthenticationPrincipal ClaimAccessor user) {
        return ResponseEntity.of(decisions.approve(id, Person.of(user)));
    }

    @PostMapping("/undo")
    ResponseEntity<Decisions.Undone> undo(@PathVariable UUID id, @AuthenticationPrincipal ClaimAccessor user) {
        return ResponseEntity.of(decisions.undo(id, Person.of(user)));
    }

    /** 409, with the text the reviewer is shown. */
    @ExceptionHandler({AlreadyDecidedException.class, NoLongerUndoableException.class, TakenByOtherException.class})
    ProblemDetail tooLate(RuntimeException late) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, late.getMessage());
    }
}
