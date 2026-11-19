package io.github.defiro.designer.internal;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/designer/workflows")
class WorkflowController {

    /** Refuses a draft with missing or invalid answers, naming each field and what is wrong with it. */
    @PostMapping
    ResponseEntity<ProblemDetail> create(@RequestBody WorkflowDraft draft) {
        List<Problem> problems = draft.problems();
        if (!problems.isEmpty()) {
            ProblemDetail refusal = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            refusal.setTitle("O workflow tem respostas em falta ou inválidas.");
            refusal.setProperty("errors", problems);
            return ResponseEntity.of(refusal).build();
        }
        // Creating a complete workflow comes with its own scenario («Creating a complete workflow», still @wip).
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
