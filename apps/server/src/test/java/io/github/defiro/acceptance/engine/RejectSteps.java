package io.github.defiro.acceptance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UC18 through the Engine API, as the web app calls it: returning and rejecting the task the request
 * waits at, which the Background took (ReviewSteps). Reasons are given as the history shows them,
 * «one; another».
 */
public class RejectSteps {

    @Autowired
    ReviewSteps review;

    @Autowired
    RequestSteps requests;

    @When("{person} returns the request {string} for {string} with the message {string}")
    public void returnsTheRequest(String person, String reference, String reasons, String message) {
        decide(person, "return", reasons, message);
    }

    @When("{person} returns the request {string} for {string} with a message of {int} characters")
    public void returnsWithALongMessage(String person, String reference, String reasons, int length) {
        decide(person, "return", reasons, "x".repeat(length));
    }

    @Given("{person} returned the request {string} for {string} with the message {string}")
    public void returnedTheRequest(String person, String reference, String reasons, String message) {
        decide(person, "return", reasons, message);
        assertThat(review.lastResponse().statusCode()).as(person + " returns").isEqualTo(200);
    }

    @When("{person} rejects the request {string} for {string} with the message {string}")
    public void rejectsTheRequest(String person, String reference, String reasons, String message) {
        decide(person, "reject", reasons, message);
    }

    @Given("{person} rejected the request {string} for {string} with the message {string}")
    public void rejectedTheRequest(String person, String reference, String reasons, String message) {
        decide(person, "reject", reasons, message);
        assertThat(review.lastResponse().statusCode()).as(person + " rejects").isEqualTo(200);
    }

    @Then("the decision is refused")
    public void theDecisionIsRefused() {
        assertThat(review.lastResponse().statusCode()).isEqualTo(422);
    }

    @Then("^the request is (returned|rejected), at \"([^\"]+)\"$")
    public void theRequestIs(String status, String step) {
        assertThat(review.lastResponse().statusCode()).as("the decision").isEqualTo(200);
        assertThat(asTheRequester().getString("status")).isEqualTo(status);
        review.theRequestIsStillAt(step);
    }

    @Then("the newest entry of its history is {string}, with the message {string}")
    public void theNewestEntryIs(String entry, String message) {
        assertThat(review.lastResponse().statusCode()).as("the decision").isEqualTo(200);
        Map<String, Object> newest = asTheRequester().getMap("history[-1]");
        assertThat(newest).containsEntry("text", entry).containsEntry("message", message);
    }

    private void decide(String person, String decision, String reasons, String message) {
        UUID task = review.waitingTask();
        List<String> chosen = RequestSteps.reasons(reasons);
        Instant from = Instant.now();
        review.decided(
                person,
                task,
                from,
                review.browserOf(person)
                        .request()
                        .contentType(ContentType.JSON)
                        .body(Map.of("reasons", chosen, "message", message))
                        .post("/api/engine/tasks/" + task + "/" + decision));
    }

    private JsonPath asTheRequester() {
        return review.requestSeenBy(requests.requester());
    }
}
