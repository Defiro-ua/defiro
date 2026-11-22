package io.github.defiro.acceptance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.github.defiro.FakeEntraId;
import io.github.defiro.FakeEntraId.Account;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.engine.internal.ProcedureSeed;
import io.github.defiro.engine.internal.ProcedureSeed.Answer;
import io.github.defiro.engine.internal.ProcedureSeed.Requester;
import io.github.defiro.engine.internal.ProcedureSeed.Submitted;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.Transition;
import io.github.defiro.spec.WorkflowSpecification;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The requests the review stories (UC17, UC18, UC19) start from. Until UC13 (Dinis) exists there is
 * no request form, so a request is put in the database by {@link ProcedureSeed}, on a workflow
 * deployed straight to the Engine, as the Designer does when it publishes one. Each scenario gets a
 * new instance, so the fields hold that scenario's workflows, request and files.
 */
public class RequestSteps {

    // The plugins of the built-in steps (registry), at the version the Designer publishes.
    private static final String PLUGIN_VERSION = "0.1.0";
    private static final List<String> DECISIONS = List.of("approved", "returned", "rejected");

    // Until UC04 lets the Designer define the form, the answers keep the field keys of the
    // prototype's «Ajudas de custo» (requestFields in apps/web/src/domain/requests.ts).
    private static final Map<String, String> FIELDS = Map.of(
            "Motivo", "motivo",
            "Destino", "destino",
            "Descrição", "desc",
            "Partida", "partida",
            "Regresso", "regresso",
            "Datas do certificado", "cert",
            "Transporte", "transporte",
            "NIF", "nif",
            "IBAN", "iban");

    private final FakeEntraId entraId = FakeEntraId.instance();
    private final Map<String, Deployed> workflows = new HashMap<>();
    private final Map<String, byte[]> attached = new LinkedHashMap<>();
    private Submitted request;

    @Autowired
    Deployment deployment;

    @Autowired
    ProcedureSeed seed;

    record Deployed(UUID draft, int version, WorkflowSpecification specification) {}

    @Given("the workflow {string} has the path")
    public void theWorkflowHasThePath(String name, DataTable path) {
        List<Map<String, String>> rows = path.asMaps();
        List<StepSpecification> steps = new ArrayList<>();
        for (int k = 0; k < rows.size(); k++) {
            steps.add(new StepSpecification(
                    UUID.randomUUID().toString(),
                    new PluginRef(plugin(k, rows.size()), PLUGIN_VERSION),
                    rows.get(k).get("step"),
                    rows.get(k).get("handled by"),
                    null,
                    List.of(),
                    List.of()));
        }
        WorkflowSpecification specification =
                new WorkflowSpecification(WorkflowSpecification.SCHEMA_VERSION, name, steps, transitions(steps));
        UUID draft = UUID.randomUUID();
        // Published by the Workflow Manager of the mockup.
        String publishedBy = entraId.account("Sofia Almeida").id();
        int version = deployment.deploy(specification, new Publication(publishedBy, draft, 1));
        workflows.put(name, new Deployed(draft, version, specification));
    }

    @Given("{person} submitted the request {string} for {string} with")
    public void submittedTheRequest(String person, String reference, String workflow, DataTable answers) {
        Deployed deployed = workflows.get(workflow);
        assertThat(deployed).as("the workflow " + workflow).isNotNull();
        List<StepSpecification> steps = deployed.specification().steps();
        Account account = entraId.account(person);
        request = seed.submit(
                reference,
                deployed.draft(),
                deployed.version(),
                steps.get(0).id(),
                steps.get(1),
                new Requester(account.id(), account.name(), account.jobTitle(), account.unit(), account.manager()),
                answers.asMaps().stream()
                        .map(row -> new Answer(field(row.get("field")), row.get("field"), row.get("answer")))
                        .toList());
    }

    @Given("with the files {string} and {string}")
    public void withTheFiles(String file, String otherFile) {
        attach(file);
        attach(otherFile);
    }

    /** The bytes a file of the request was attached with. */
    byte[] attached(String filename) {
        byte[] content = attached.get(filename);
        assertThat(content).as("the file " + filename + " was attached").isNotNull();
        return content;
    }

    private void attach(String filename) {
        assertThat(request).as("a submitted request").isNotNull();
        // Stands for the file's bytes: the tests only check they come back the same.
        byte[] content = ("Conteúdo de " + filename).getBytes(StandardCharsets.UTF_8);
        seed.attach(request, filename, content);
        attached.put(filename, content);
    }

    // A path the Designer accepts: it starts with the request and ends with the final validation.
    private static String plugin(int step, int steps) {
        if (step == 0) {
            return "submission";
        }
        return step == steps - 1 ? "final-validation" : "review";
    }

    // As the Designer does (WorkflowDraft.specification): an approved request goes to the next step,
    // or ends after the last one; a returned one goes back to the request; a rejected one ends.
    private static List<Transition> transitions(List<StepSpecification> steps) {
        String requestStep = steps.getFirst().id();
        List<Transition> transitions = new ArrayList<>();
        for (int k = 0; k < steps.size(); k++) {
            String from = steps.get(k).id();
            String next = k + 1 < steps.size() ? steps.get(k + 1).id() : null;
            if (k == 0) {
                transitions.add(new Transition(from, "submitted", next));
                continue;
            }
            for (String outcome : DECISIONS) {
                String to = switch (outcome) {
                    case "returned" -> requestStep;
                    case "rejected" -> null;
                    default -> next;
                };
                transitions.add(new Transition(from, outcome, to));
            }
        }
        return transitions;
    }

    private static String field(String label) {
        String key = FIELDS.get(label);
        assertThat(key).as("the field key of " + label).isNotNull();
        return key;
    }
}
