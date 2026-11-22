package io.github.defiro.acceptance.designer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.hamcrest.Matchers.contains;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UC05: who handles each step, and who can make the request, from the organization's groups and
 * units. The draft, Sofia's session and the browser are the scenario's own, kept by
 * {@link CreateWorkflowSteps}.
 */
public class AssignGroupsSteps {

    private static final String GROUPS = "/api/designer/groups";

    @Autowired
    CreateWorkflowSteps designer;

    private Response options;
    private String asked;

    @When("she asks who can handle a step")
    public void sheAsksWhoCanHandleAStep() {
        ask("handlers");
    }

    @When("she asks who can make the request")
    public void sheAsksWhoCanMakeTheRequest() {
        ask("requesters");
    }

    @Then("she can choose {string}, {string}, {string}, {string}, {string} and {string}")
    public void sheCanChoose(String a, String b, String c, String d, String e, String f) {
        options.then().statusCode(200).body(asked + ".name", contains(a, b, c, d, e, f));
    }

    @Given("the step {string} is handled by {string}")
    public void theStepIsHandledBy(String step, String handledBy) {
        designer.theWorkflowIsComplete();
        designer.stepNamed(step).put("handledBy", handledBy);
        designer.saveDraft();
    }

    @Given("the step {string} is handled by nobody")
    public void theStepIsHandledByNobody(String step) {
        theStepIsHandledBy(step, null);
    }

    @Given("the request can be made by {string}")
    public void theRequestCanBeMadeBy(String handledBy) {
        designer.theWorkflowIsComplete();
        designer.stepNamed("Pedido").put("handledBy", handledBy);
        designer.saveDraft();
    }

    @When("she adds a review step in the designer")
    public void sheAddsAReviewStepInTheDesigner() {
        designer.act();
        screen().signInAs("Sofia Almeida");
        screen().openDraft(designer.draftId(), "percurso");
        screen().addReview();
    }

    @Then("the step's screen offers {string}, {string}, {string}, {string}, {string} and {string}")
    public void theStepsScreenOffers(String a, String b, String c, String d, String e, String f) {
        assertThat(screen().handlers()).hasCount(6);
        for (String handler : new String[] {a, b, c, d, e, f}) {
            assertThat(screen().handler(handler)).isVisible();
        }
    }

    @When("she moves on without choosing who handles it")
    public void sheMovesOnWithoutChoosingWhoHandlesIt() {
        screen().stepName().fill("Revisão");
        screen().goOn();
    }

    @Then("she is told {string}")
    public void sheIsTold(String message) {
        assertThat(screen().fieldError(message)).isVisible();
        assertThat(screen().question()).containsText("Como se chama o passo 2 e quem o trata?");
    }

    @Then("the path shows {string} made by {string} and {string} handled by {string}")
    public void thePathShowsWhoHandlesEachStep(String request, String requesters, String step, String handler) {
        assertThat(screen().pathStep(request)).containsText("Quem pode pedir" + requesters);
        assertThat(screen().pathStep(step)).containsText("Quem trata" + handler);
    }

    private void ask(String who) {
        designer.act();
        asked = who;
        options = designer.asSofia().get(GROUPS);
    }

    private DesignerScreen screen() {
        return designer.screen();
    }
}
