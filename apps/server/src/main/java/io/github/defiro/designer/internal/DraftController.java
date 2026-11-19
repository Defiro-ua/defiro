package io.github.defiro.designer.internal;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The drafts a Workflow Manager designs, one change at a time. Each answer is the whole draft. */
@RestController
@RequestMapping("/api/designer/drafts")
class DraftController {

    private final Drafts drafts;

    DraftController(Drafts drafts) {
        this.drafts = drafts;
    }

    record Name(String name) {}

    record NewStep(String name, String handledBy, Integer deadline) {}

    record DraftView(UUID id, String name, List<Step> steps) {
        DraftView(UUID id, WorkflowDraft draft) {
            this(id, draft.name(), draft.steps());
        }
    }

    @PostMapping
    ResponseEntity<DraftView> start(@RequestBody Name request) {
        WorkflowDraft draft = WorkflowDraft.named(request.name());
        UUID id = drafts.add(draft);
        return ResponseEntity.created(URI.create("/api/designer/drafts/" + id)).body(new DraftView(id, draft));
    }

    @GetMapping("/{id}")
    DraftView show(@PathVariable UUID id) {
        return new DraftView(id, find(id));
    }

    @PatchMapping("/{id}")
    DraftView rename(@PathVariable UUID id, @RequestBody Name request) {
        return change(id, draft -> draft.withName(request.name()));
    }

    @PostMapping("/{id}/steps")
    DraftView addStep(@PathVariable UUID id, @RequestBody NewStep step) {
        return change(id, draft -> draft.with(Step.review(step.name(), step.handledBy(), step.deadline())));
    }

    private DraftView change(UUID id, UnaryOperator<WorkflowDraft> change) {
        WorkflowDraft changed = change.apply(find(id));
        drafts.replace(id, changed);
        return new DraftView(id, changed);
    }

    private WorkflowDraft find(UUID id) {
        return drafts.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
