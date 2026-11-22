package io.github.defiro.acceptance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.util.List;

/**
 * The review stories (UC17, UC18, UC19) through the Engine API, as the web app calls it. Each person
 * signs in through Entra ID, as in UC01, in their own browser. Each scenario gets a new instance.
 */
public class ReviewSteps {

    private static final String INBOX = "/api/engine/inbox";

    private final People people = new People();
    private Response response;

    @When("{person} looks at the inbox")
    public void looksAtTheInbox(String person) {
        response = people.of(person).request().get(INBOX);
    }

    @Then("^the request \"([^\"]+)\" is (there|not there)$")
    public void theRequestIs(String reference, String shown) {
        assertThat(response.statusCode()).isEqualTo(200);
        List<String> references = response.jsonPath().getList("reference", String.class);
        if (shown.equals("there")) {
            assertThat(references).as("the requests in the inbox").contains(reference);
        } else {
            assertThat(references).as("the requests in the inbox").doesNotContain(reference);
        }
    }
}
