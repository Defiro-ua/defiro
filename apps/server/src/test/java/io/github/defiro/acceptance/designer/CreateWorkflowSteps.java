package io.github.defiro.acceptance.designer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;

import com.microsoft.playwright.Locator;
import io.cucumber.java.After;
import io.cucumber.java.Before;
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
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Value;

/**
 * UC03 through the Designer API. As in docs/sequence_diagram.txt, the browser holds the draft and
 * changes it, and the Designer creates, saves (whole), loads and publishes it: these steps play the
 * browser. People sign in through Entra ID, as in UC01, and keep a session. Each scenario gets a new
 * instance, so the fields hold that scenario's people, draft and last answer.
 *
 * <p>The {@code @browser} scenarios also open the web app in a real browser ({@link DesignerScreen}).
 * Some steps read the same as a Given and as a Then, such as "the path is ...", and Cucumber gives
 * both one method: before any When, the step builds what it says through the API; after a When, it
 * checks it, on the screen in a browser scenario, otherwise through the API.
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
    private DesignerScreen screen;
    private boolean acted;

    // The port of the server under test, which also serves the web app
    @Value("${local.server.port}")
    int port;

    @Before("@browser")
    public void openTheBrowser() {
        screen = new DesignerScreen(port);
    }

    @After("@browser")
    public void closeTheBrowser() {
        screen.close();
    }

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
        acted = true;
        steps().add(reviewStep(name, handledBy, deadline));
        save();
    }

    @Given("the path is {string}, {string}")
    public void thePathIs(String first, String second) {
        thePathIs(List.of(first, second));
    }

    @Given("the path is {string}, {string}, {string}")
    public void thePathIs(String first, String second, String third) {
        thePathIs(List.of(first, second, third));
    }

    @Given("the path ends with {string}")
    public void thePathEndsWith(String step) {
        if (!acted) {
            steps().add(finalValidationStep());
            save();
            openThePath();
        }
        if (screen != null) {
            assertThat(screen.path().last()).containsText(step);
        } else {
            load().then().body("steps[-1].name", equalTo(step));
        }
    }

    @Then("the path has one {string}")
    public void thePathHasOne(String step) {
        assertThat(screen.path().filter(new Locator.FilterOptions().setHasText(step)))
                .hasCount(1);
    }

    @When("she moves {string} up")
    public void sheMovesUp(String step) {
        acted = true;
        screen.moveUp(step);
    }

    @When("she removes {string}")
    public void sheRemoves(String step) {
        acted = true;
        screen.remove(step);
    }

    @When("she adds a final validation step")
    public void sheAddsAFinalValidationStep() {
        acted = true;
        screen.addFinalValidation();
    }

    @When("Sofia Almeida opens defiro")
    public void sofiaAlmeidaOpensDefiro() {
        acted = true;
        screen.signInAs("Sofia Almeida");
    }

    @Then("«Criar workflow» is the main button of her side menu")
    public void criarWorkflowIsTheMainButtonOfHerSideMenu() {
        assertThat(screen.menu().first()).hasText("Criar workflow");
    }

    @When("she starts creating a workflow")
    public void sheStartsCreatingAWorkflow() {
        acted = true;
        screen.signInAs("Sofia Almeida");
        screen.openMenuItem("Criar workflow");
    }

    @Then("^she sees the screen for the name with «(.+)»$")
    public void sheSeesTheScreenForTheNameWith(String kicker) {
        assertThat(screen.question()).containsText("Como se chama o workflow?");
        assertThat(screen.kicker(kicker)).isVisible();
    }

    @Given("she named the workflow and moved on")
    public void sheNamedTheWorkflowAndMovedOn() {
        screen.signInAs("Sofia Almeida");
        screen.openDraft(draftId, null);
        screen.workflowName().fill((String) draft.get("name"));
        screen.goOn();
        assertThat(screen.question()).containsText("Quem pode fazer este pedido?");
    }

    @When("she goes back")
    public void sheGoesBack() {
        acted = true;
        screen.goBack();
    }

    @Then("the name {string} is still there")
    public void theNameIsStillThere(String name) {
        assertThat(screen.workflowName()).hasValue(name);
    }

    /** The draft's path is these steps: built through the API before any When, then checked. */
    private void thePathIs(List<String> names) {
        if (!acted) {
            names.stream()
                    .skip(1)
                    .forEach(name -> steps().add(reviewStep(name, "Chefias da unidade do requerente", 5)));
            save();
            openThePath();
        }
        if (screen != null) {
            assertThat(screen.path()).containsText(names.toArray(String[]::new));
        } else {
            load().then().body("steps.name", contains(names.toArray()));
        }
    }

    /** In a browser scenario, Sofia opens the draft on its path screen */
    private void openThePath() {
        if (screen != null) {
            screen.signInAs("Sofia Almeida");
            screen.openDraft(draftId, "percurso");
        }
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
        acted = true;
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
        acted = true;
        response = as(sofia).post(DRAFTS + "/" + draftId + "/publish");
    }

    @When("she opens the list of workflows")
    public void sheOpensTheListOfWorkflows() {
        acted = true;
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

    private Map<String, Object> finalValidationStep() {
        Map<String, Object> step = reviewStep("Validação final", "Divisão Financeira", 10);
        step.put("plugin", stepType("final-validation"));
        return step;
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
        Assertions.assertThat(browser.signIn(name).statusCode())
                .as(name + " signs in")
                .isEqualTo(302);
        Assertions.assertThat(browser.profile().statusCode()).isEqualTo(200);
        Assertions.assertThat(browser.cookie("XSRF-TOKEN"))
                .as("the CSRF cookie the web app sends back")
                .isNotNull();
    }

    private static RequestSpecification as(Browser person) {
        return person.request().contentType(ContentType.JSON);
    }
}
