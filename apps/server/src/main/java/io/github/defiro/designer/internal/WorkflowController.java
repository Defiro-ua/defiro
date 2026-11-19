package io.github.defiro.designer.internal;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The workflows created from drafts, as «Workflows» lists them. */
// Only a Workflow Manager designs workflows: the app role that identity turns into a role.
@PreAuthorize("hasRole('WorkflowManager')")
@RestController
@RequestMapping("/api/designer/workflows")
class WorkflowController {

    private final Drafts drafts;
    private final Workflows workflows;

    WorkflowController(Drafts drafts, Workflows workflows) {
        this.drafts = drafts;
        this.workflows = workflows;
    }

    record FromDraft(UUID draft) {}

    record WorkflowView(UUID id, String name, int version, WorkflowStatus status) {
        WorkflowView(Workflow workflow) {
            this(workflow.id(), workflow.name(), workflow.version(), workflow.status());
        }
    }

    @PostMapping
    ResponseEntity<WorkflowView> create(@RequestBody FromDraft request) {
        WorkflowDraft draft =
                drafts.find(request.draft()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Workflow workflow = Workflow.firstVersionOf(draft);
        workflows.add(workflow);
        return ResponseEntity.created(URI.create("/api/designer/workflows/" + workflow.id()))
                .body(new WorkflowView(workflow));
    }

    @GetMapping
    List<WorkflowView> list() {
        return workflows.all().stream().map(WorkflowView::new).toList();
    }

    /** Refuses a draft with missing or invalid answers, naming each field and what is wrong with it. */
    @ExceptionHandler
    ProblemDetail refuse(IncompleteWorkflowException refused) {
        ProblemDetail refusal = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        refusal.setTitle("O workflow tem respostas em falta ou inválidas.");
        refusal.setProperty("errors", refused.problems());
        return refusal;
    }
}
