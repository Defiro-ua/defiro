package io.github.defiro.acceptance.designer;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;

/**
 * UC03 through the Designer API. Each scenario gets a new instance, so the fields hold that
 * scenario's Workflow Manager, her draft and the last answer.
 */
public class CreateWorkflowSteps {

    // The app role in Entra ID that UC01 reads from the token's "roles" claim.
    private static final String WORKFLOW_MANAGER = "WorkflowManager";

    private final Map<String, Object> workflow = new HashMap<>();
    private String authorization;
    private Response response;

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
        workflow.put("name", name);
    }

    @Given("the workflow has no name")
    public void theWorkflowHasNoName() {
        workflow.put("name", "");
    }

    @When("she creates the workflow")
    public void sheCreatesTheWorkflow() {
        response = given().header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(ContentType.JSON)
                .body(workflow)
                .post("/api/designer/workflows");
    }

    @Then("it is refused with {string} on the workflow's name")
    public void itIsRefusedOnTheWorkflowsName(String message) {
        response.then()
                .statusCode(422)
                .body("errors", hasItem(allOf(hasEntry("field", "name"), hasEntry("message", message))));
    }
}
