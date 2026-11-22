package io.github.defiro.acceptance;

import io.cucumber.java.en.Then;
import io.restassured.response.Response;

/**
 * The steps on what a person may do, shared by every story: the step classes give it the answer to
 * the person's last request. Each scenario gets a new instance, which the step classes share.
 */
public class AccessSteps {

    private Response answer;

    /** The answer to the last request of the scenario's person. */
    public void answered(Response response) {
        this.answer = response;
    }

    @Then("(s)he is not allowed to")
    public void isNotAllowedTo() {
        answer.then().statusCode(403);
    }
}
