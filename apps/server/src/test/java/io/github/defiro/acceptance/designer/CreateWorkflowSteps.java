package io.github.defiro.acceptance.designer;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;

/**
 * UC03 through the Designer API. Each scenario gets a new instance, so the fields hold that
 * scenario's Workflow Manager, her draft and the last answer.
 *
 * <p>Some steps read the same as a Given and as a Then, for example "the path is ...", and Cucumber
 * gives both one method. Before any When, such a step builds what it says; after a When, it only
 * checks it.
 */
public class CreateWorkflowSteps {

    // The app role in Entra ID that UC01 reads from the token's "roles" claim.
    private static final String WORKFLOW_MANAGER = "WorkflowManager";

    private String authorization;
    private Response response;
    private String draftId;
    private String draft;
    private boolean acted;

    @Given("Sofia Almeida is a Workflow Manager")
    public void sofiaAlmeidaIsAWorkflowManager() {
        authorization = bearer(
                TRUSTED_KEY,
                claims -> claims.subject("sofia.almeida")
                        .claim("name", "Sofia Almeida")
                        .claim("roles", List.of(WORKFLOW_MANAGER)));
    }

    @Given("she is designing the workflow {string}")
    public void sheIsDesigningTheWorkflow(String name) {
        draftId = asSofia()
                .body(Map.of("name", name))
                .post("/api/designer/drafts")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        draft = "/api/designer/drafts/" + draftId;
    }

    @When("she adds the review step {string} handled by {string} with a deadline of {int} working days")
    public void sheAddsTheReviewStep(String name, String handledBy, int deadline) {
        acted = true;
        addReviewStep(name, handledBy, deadline);
    }

    @Given("the path is {string}, {string}")
    public void thePathIs(String first, String second) {
        thePathIs(List.of(first, second));
    }

    @Given("the path is {string}, {string}, {string}")
    public void thePathIs(String first, String second, String third) {
        thePathIs(List.of(first, second, third));
    }

    @Given("the workflow has no name")
    public void theWorkflowHasNoName() {
        asSofia().body(Map.of("name", "")).patch(draft).then().statusCode(200);
    }

    @When("she moves {string} up")
    public void sheMovesUp(String step) {
        acted = true;
        asSofia()
                .body(Map.of("direction", "UP"))
                .post(draft + "/steps/" + stepId(step) + "/move")
                .then()
                .statusCode(200);
    }

    @When("she removes {string}")
    public void sheRemoves(String step) {
        acted = true;
        asSofia().delete(draft + "/steps/" + stepId(step)).then().statusCode(200);
    }

    @When("she adds a final validation step")
    public void sheAddsAFinalValidationStep() {
        acted = true;
        addFinalValidation();
    }

    @Given("the path ends with {string}")
    public void thePathEndsWith(String step) {
        if (!acted) {
            addFinalValidation();
        }
        asSofia().get(draft).then().statusCode(200).body("steps[-1].name", equalTo(step));
    }

    @Then("the path has one {string}")
    public void thePathHasOne(String step) {
        asSofia()
                .get(draft)
                .then()
                .statusCode(200)
                .body("steps.findAll { it.name == '%s' }.size()".formatted(step), equalTo(1));
    }

    @When("she creates the workflow")
    public void sheCreatesTheWorkflow() {
        acted = true;
        response = asSofia().body(Map.of("draft", draftId)).post("/api/designer/workflows");
    }

    @Then("it is refused with {string} on the workflow's name")
    public void itIsRefusedOnTheWorkflowsName(String message) {
        response.then()
                .statusCode(422)
                .body("errors", hasItem(allOf(hasEntry("field", "name"), hasEntry("message", message))));
    }

    private void thePathIs(List<String> names) {
        if (!acted) {
            // The draft starts with «Pedido»: every other name becomes a review step.
            names.stream().skip(1).forEach(name -> addReviewStep(name, "Chefias da unidade do requerente", 5));
        }
        asSofia().get(draft).then().statusCode(200).body("steps.name", contains(names.toArray()));
    }

    private void addReviewStep(String name, String handledBy, int deadline) {
        asSofia()
                .body(Map.of("type", "REVIEW", "name", name, "handledBy", handledBy, "deadline", deadline))
                .post(draft + "/steps")
                .then()
                .statusCode(200);
    }

    private void addFinalValidation() {
        asSofia()
                .body(Map.of("type", "FINAL_VALIDATION"))
                .post(draft + "/steps")
                .then()
                .statusCode(200);
    }

    private String stepId(String name) {
        return asSofia()
                .get(draft)
                .then()
                .statusCode(200)
                .extract()
                .path("steps.find { it.name == '%s' }.id".formatted(name));
    }

    private RequestSpecification asSofia() {
        return given().header(HttpHeaders.AUTHORIZATION, authorization).contentType(ContentType.JSON);
    }
}
