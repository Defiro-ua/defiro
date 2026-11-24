package io.github.defiro.designer.internal;

import io.github.defiro.registry.PluginCatalogue;
import io.github.defiro.spec.PluginRef;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The drafts of the Workflow Managers. The browser changes a draft and saves it whole; publishing
 * checks it and deploys it to the Engine. «Workflows» lists the drafts, with the version each one
 * was published as.
 */
// Only a Workflow Manager designs workflows: the app role that identity turns into a role.
@PreAuthorize("hasRole('WorkflowManager')")
@RestController
@RequestMapping("/api/designer/drafts")
class DraftController {

    private final Drafts drafts;
    private final Publisher publisher;
    private final PluginCatalogue catalogue;

    DraftController(Drafts drafts, Publisher publisher, PluginCatalogue catalogue) {
        this.drafts = drafts;
        this.publisher = publisher;
        this.catalogue = catalogue;
    }

    record NewDraft(String name) {}

    /**
     * A draft as the browser sees it. Once published: the version, who published it (by name) and
     * when, for «Workflows» to show «Versão N · criado por … a …».
     */
    record DraftView(
            UUID id,
            int revision,
            String name,
            List<Step> steps,
            Integer publishedVersion,
            String publishedBy,
            Instant publishedAt) {
        DraftView(StoredDraft stored) {
            this(
                    stored.id(),
                    stored.revision(),
                    stored.draft().name(),
                    stored.draft().steps(),
                    stored.published() == null ? null : stored.published().version(),
                    stored.published() == null ? null : stored.published().byName(),
                    stored.published() == null ? null : stored.published().at());
        }
    }

    @PostMapping
    ResponseEntity<DraftView> start(@RequestBody NewDraft request) {
        StoredDraft stored = drafts.add(WorkflowDraft.named(request.name(), requestPlugin()));
        return ResponseEntity.created(URI.create("/api/designer/drafts/" + stored.id()))
                .body(new DraftView(stored));
    }

    @GetMapping
    List<DraftView> list() {
        return drafts.all().stream().map(DraftView::new).toList();
    }

    @GetMapping("/{id}")
    DraftView load(@PathVariable UUID id) {
        return new DraftView(drafts.find(id).orElseThrow(DraftController::notFound));
    }

    @PutMapping("/{id}")
    DraftView save(@PathVariable UUID id, @RequestBody WorkflowDraft draft) {
        return new DraftView(drafts.save(id, draft).orElseThrow(DraftController::notFound));
    }

    @PostMapping("/{id}/publish")
    ResponseEntity<Map<String, Integer>> publish(@PathVariable UUID id, @AuthenticationPrincipal ClaimAccessor user) {
        Publisher.Who who = new Publisher.Who(user.getClaimAsString("sub"), user.getClaimAsString("name"));
        int version = publisher.publish(id, who).orElseThrow(DraftController::notFound);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("version", version));
    }

    /** Refuses a draft with missing or invalid answers, naming each field and what is wrong with it. */
    @ExceptionHandler
    ProblemDetail refuse(IncompleteWorkflowException refused) {
        ProblemDetail refusal = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        refusal.setTitle("O workflow tem respostas em falta ou inválidas.");
        refusal.setProperty("errors", refused.problems());
        return refusal;
    }

    /** The plugin of the request, as the catalogue has it. */
    private PluginRef requestPlugin() {
        return catalogue.plugins().stream()
                .filter(plugin -> plugin.name().equals(WorkflowDraft.SUBMISSION))
                .findFirst()
                .orElseThrow()
                .plugin();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
