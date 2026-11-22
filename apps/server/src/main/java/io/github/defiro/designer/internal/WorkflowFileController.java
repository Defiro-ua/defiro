package io.github.defiro.designer.internal;

import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.spec.WorkflowSpecification;
import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Workflows as files (docs/workflow-specification.md). A created workflow goes out as its published
 * version, exactly as the Engine keeps it; a file comes in as a new draft, which the Workflow
 * Manager reviews in the designer and creates, so the Designer checks it like any other draft.
 */
// Only a Workflow Manager designs workflows: the app role that identity turns into a role.
@PreAuthorize("hasRole('WorkflowManager')")
@RestController
@RequestMapping("/api/designer/drafts")
class WorkflowFileController {

    private static final String NOT_A_WORKFLOW = "O ficheiro não é um workflow da defiro.";

    private final Drafts drafts;
    private final Deployment deployment;
    private final JsonMapper json;

    WorkflowFileController(Drafts drafts, Deployment deployment, JsonMapper json) {
        this.drafts = drafts;
        this.deployment = deployment;
        this.json = json;
    }

    /** The last published version of the workflow, as a file named after it: ajudas-de-custo-v1.json */
    @GetMapping("/{id}/specification")
    ResponseEntity<WorkflowSpecification> download(@PathVariable UUID id) {
        Published published = drafts.find(id)
                .map(StoredDraft::published)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        WorkflowSpecification specification = deployment
                .version(id, published.version())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String file = slug(specification.name()) + "-v" + published.version() + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file).build().toString())
                .body(specification);
    }

    /** A workflow file becomes a new draft, to review in the designer before creating it. */
    @PostMapping("/import")
    ResponseEntity<DraftController.DraftView> load(@RequestBody String file) {
        StoredDraft stored = drafts.add(WorkflowDraft.from(read(file)));
        return ResponseEntity.created(URI.create("/api/designer/drafts/" + stored.id()))
                .body(new DraftController.DraftView(stored));
    }

    @ExceptionHandler
    ProblemDetail refuse(NotAWorkflowFile refused) {
        ProblemDetail refusal = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
        refusal.setTitle("O ficheiro não foi carregado.");
        return refusal;
    }

    /** The version of the format comes first, so a file from a later defiro says so. */
    private WorkflowSpecification read(String file) {
        JsonNode tree;
        try {
            tree = json.readTree(file);
        } catch (JacksonException e) {
            throw new NotAWorkflowFile(NOT_A_WORKFLOW);
        }
        JsonNode schemaVersion = tree == null ? null : tree.get("schemaVersion");
        if (schemaVersion == null || !schemaVersion.isInt()) {
            throw new NotAWorkflowFile(NOT_A_WORKFLOW);
        }
        if (schemaVersion.asInt() != WorkflowSpecification.SCHEMA_VERSION) {
            throw new NotAWorkflowFile("O ficheiro é de uma versão do formato que esta defiro não conhece ("
                    + schemaVersion.asInt() + ").");
        }
        JsonNode steps = tree.get("steps");
        if (steps == null || !steps.isArray()) {
            throw new NotAWorkflowFile(NOT_A_WORKFLOW);
        }
        if (steps.isEmpty()) {
            throw new NotAWorkflowFile("O ficheiro não tem passos.");
        }
        // The transitions are made again from the path when publishing, so a file may leave them out.
        if (!tree.has("transitions") && tree instanceof ObjectNode object) {
            object.putArray("transitions");
        }
        try {
            return json.treeToValue(tree, WorkflowSpecification.class);
        } catch (JacksonException | NullPointerException e) {
            throw new NotAWorkflowFile(NOT_A_WORKFLOW);
        }
    }

    /** «Ajudas de custo – Ação Social» is ajudas-de-custo-acao-social */
    static String slug(String name) {
        String plain = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-", "")
                .replaceAll("-$", "");
        return plain.isEmpty() ? "workflow" : plain;
    }

    static class NotAWorkflowFile extends RuntimeException {
        NotAWorkflowFile(String message) {
            super(message);
        }
    }
}
