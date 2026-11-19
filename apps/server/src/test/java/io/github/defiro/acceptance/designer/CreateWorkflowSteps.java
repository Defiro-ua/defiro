package io.github.defiro.acceptance.designer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.defiro.FakeEntraId;
import io.github.defiro.FakeEntraId.Account;
import io.github.defiro.acceptance.Browser;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UC03 through the Designer API. As in docs/sequence_diagram.txt, the browser holds the draft and
 * changes it, and the Designer creates, saves (whole), loads and publishes it: these steps play the
 * browser. People sign in through Entra ID, as in UC01, and keep a session. Each scenario gets a new instance, so the fields hold that scenario's people, draft and
 * last answer.
 */
public class CreateWorkflowSteps {

    // The app role in Entra ID that UC01 reads from the token's "roles" claim.
    private static final String WORKFLOW_MANAGER = "WorkflowManager";
    private static final String DRAFTS = "/api/designer/drafts";

    private final FakeEntraId entraId = FakeEntraId.instance();
    private final Browser sofia = new Browser();
    private final Browser ana = new Browser();
    private String draftId;
    private Map<String, Object> draft;
    private Response response;
    private String lastStep;

    @Given("Sofia Almeida is a Workflow Manager")
    public void sofiaAlmeidaIsAWorkflowManager() {
        entraId.save(new Account(
                "Sofia Almeida", "Modernização Administrativa", "Gestora de workflows", "Rui Matos", WORKFLOW_MANAGER));
        signIn(sofia, "Sofia Almeida");
    }

    @Given("Ana Ferreira is not a Workflow Manager")
    public void anaFerreiraIsNotAWorkflowManager() {
        entraId.save(new Account("Ana Ferreira", "Divisão de Obras Municipais", "Técnica Superior", "Rui Matos", null));
        signIn(ana, "Ana Ferreira");
    }

    @Given("she is designing the workflow {string}")
    public void sheIsDesigningTheWorkflow(String name) {
        draft = as(sofia)
                .body(Map.of("name", name))
                .post(DRAFTS)
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getMap("$");
        draftId = (String) draft.get("id");
    }

    @When("she starts designing the workflow {string}")
    public void sheStartsDesigningTheWorkflow(String name) {
        response = as(ana).body(Map.of("name", name)).post(DRAFTS);
    }

    @When("she tries to create the workflow Sofia Almeida is designing")
    public void sheTriesToCreateTheWorkflowSofiaIsDesigning() {
        response = as(ana).post(DRAFTS + "/" + draftId + "/publish");
    }

    @Then("she is not allowed to")
    public void sheIsNotAllowedTo() {
        response.then().statusCode(403);
    }

    @When("she adds the review step {string} handled by {string} with a deadline of {int} working days")
    public void sheAddsTheReviewStep(String name, String handledBy, int deadline) {
        steps().add(reviewStep(name, handledBy, deadline));
        save();
    }

    @Then("the path is {string}, {string}")
    public void thePathIs(String first, String second) {
        load().then().body("steps.name", contains(first, second));
    }

    @Given("the step {string} has no reasons")
    public void theStepHasNoReasons(String step) {
        giveReasons(step, List.of(), List.of());
    }

    @Given("the step {string} has a return reason with no name")
    public void theStepHasAReturnReasonWithNoName(String step) {
        giveReasons(step, List.of(""), List.of());
    }

    @When("she gives {string} the return reason {string} and the reject reason {string}")
    public void sheGivesTheReasons(String step, String returnReason, String rejectReason) {
        giveReasons(step, List.of(returnReason), List.of(rejectReason));
    }

    @Then("{string} can be returned for {string} only")
    public void canBeReturnedForOnly(String step, String reason) {
        lastStep = step;
        load().then().body(stepPath(step) + ".returnReasons", contains(reason));
    }

    @Then("it can be rejected for {string} only")
    public void itCanBeRejectedForOnly(String reason) {
        load().then().body(stepPath(lastStep) + ".rejectReasons", contains(reason));
    }

    @Given("the workflow has no name")
    public void theWorkflowHasNoName() {
        draft.put("name", "");
        save();
    }

    @Given("the workflow is complete")
    public void theWorkflowIsComplete() {
        giveReasons("Validação da chefia", List.of("Preenchido incorretamente"), List.of("Pedido não autorizado"));
    }

    @Given("{string} was created")
    public void wasCreated(String name) {
        draft.put("name", name);
        theWorkflowIsComplete();
        sheCreatesTheWorkflow();
        response.then().statusCode(201);
    }

    @When("she creates the workflow")
    public void sheCreatesTheWorkflow() {
        response = as(sofia).post(DRAFTS + "/" + draftId + "/publish");
    }

    @When("she opens the list of workflows")
    public void sheOpensTheListOfWorkflows() {
        response = as(sofia).get(DRAFTS);
    }

    @Then("it is refused with {string}")
    public void itIsRefusedWith(String message) {
        response.then().statusCode(422).body("errors.message", hasItem(message));
    }

    @Then("it is refused with {string} on the workflow's name")
    public void itIsRefusedOnTheWorkflowsName(String message) {
        response.then()
                .statusCode(422)
                .body("errors", hasItem(allOf(hasEntry("field", "name"), hasEntry("message", message))));
    }

    @Then("{string} is active as version {int}")
    public void isActiveAsVersion(String name, int version) {
        response.then().statusCode(201).body("version", equalTo(version));
        String workflow = "find { it.id == '%s' }".formatted(draftId);
        as(sofia)
                .get(DRAFTS)
                .then()
                .statusCode(200)
                .body(workflow + ".name", equalTo(name))
                .body(workflow + ".publishedVersion", equalTo(version));
    }

    @Then("{string} is listed")
    public void isListed(String name) {
        response.then().statusCode(200).body("find { it.id == '%s' }.name".formatted(draftId), equalTo(name));
    }

    /** Gives the step these reasons, adding it first, complete, if the path does not have it. */
    private void giveReasons(String name, List<String> returnReasons, List<String> rejectReasons) {
        Map<String, Object> step = steps().stream()
                .filter(each -> name.equals(each.get("name")))
                .findFirst()
                .orElseGet(() -> {
                    Map<String, Object> added = reviewStep(name, "Chefias da unidade do requerente", 5);
                    steps().add(added);
                    return added;
                });
        step.put("returnReasons", returnReasons);
        step.put("rejectReasons", rejectReasons);
        save();
    }

    private Map<String, Object> reviewStep(String name, String handledBy, int deadline) {
        Map<String, Object> step = new HashMap<>();
        step.put("id", UUID.randomUUID().toString());
        step.put("plugin", stepType("review"));
        step.put("name", name);
        step.put("handledBy", handledBy);
        step.put("deadline", deadline);
        step.put("returnReasons", List.of());
        step.put("rejectReasons", List.of());
        return step;
    }

    /** The step type the palette offers under this plugin name, as {name, version}. */
    private Map<String, Object> stepType(String plugin) {
        Map<String, Object> type = as(sofia)
                .get("/api/designer/step-types")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("find { it.name == '%s' }".formatted(plugin));
        return Map.of("name", type.get("name"), "version", type.get("version"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> steps() {
        if (!(draft.get("steps") instanceof ArrayList)) {
            draft.put("steps", new ArrayList<>((List<Map<String, Object>>) draft.get("steps")));
        }
        return (List<Map<String, Object>>) draft.get("steps");
    }

    private void save() {
        as(sofia)
                .body(Map.of("name", draft.get("name"), "steps", steps()))
                .put(DRAFTS + "/" + draftId)
                .then()
                .statusCode(200);
    }

    private Response load() {
        Response loaded = as(sofia).get(DRAFTS + "/" + draftId);
        loaded.then().statusCode(200);
        return loaded;
    }

    private static String stepPath(String name) {
        return "steps.find { it.name == '%s' }".formatted(name);
    }

    /** Signs in through Entra ID, as UC01 does, and loads the profile, which gives the CSRF cookie. */
    private static void signIn(Browser browser, String name) {
        assertThat(browser.signIn(name).statusCode()).as(name + " signs in").isEqualTo(302);
        assertThat(browser.profile().statusCode()).isEqualTo(200);
        assertThat(browser.cookie("XSRF-TOKEN"))
                .as("the CSRF cookie the web app sends back")
                .isNotNull();
    }

    private static RequestSpecification as(Browser person) {
        return person.request().contentType(ContentType.JSON);
    }
}
