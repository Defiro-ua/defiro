package io.github.defiro.acceptance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.defiro.acceptance.AccessSteps;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The review stories (UC17, UC18, UC19) through the Engine API, as the web app calls it. Each person
 * signs in through Entra ID, as in UC01, in their own browser. Each scenario gets a new instance.
 */
public class ReviewSteps {

    private static final String INBOX = "/api/engine/inbox";
    private static final String PROCEDURES = "/api/engine/procedures/";

    private final People people = new People();
    private Response response;
    private String openedFile;

    @Autowired
    RequestSteps requests;

    @Autowired
    AccessSteps access;

    @When("{person} looks at the inbox")
    public void looksAtTheInbox(String person) {
        response = people.of(person).request().get(INBOX);
    }

    @When("{person} opens the request {string}")
    public void opensTheRequest(String person, String reference) {
        response = people.of(person).request().get(PROCEDURES + reference);
        access.answered(response);
    }

    @When("{person} opens the file {string} of the request {string}")
    public void opensTheFile(String person, String filename, String reference) {
        openedFile = filename;
        response = people.of(person).request().get(PROCEDURES + reference + "/files/" + requests.fileId(filename));
        access.answered(response);
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

    @Then("(s)he sees the answer {string} for {string} and {string} for {string}")
    public void seesTheAnswers(String answer, String field, String otherAnswer, String otherField) {
        JsonPath request = request();
        assertThat(request.getString("answers.find { it.label == '%s' }.value".formatted(field)))
                .isEqualTo(answer);
        assertThat(request.getString("answers.find { it.label == '%s' }.value".formatted(otherField)))
                .isEqualTo(otherAnswer);
    }

    @Then("(s)he sees the files {string} and {string}")
    public void seesTheFiles(String file, String otherFile) {
        assertThat(request().getList("files.filename", String.class)).contains(file, otherFile);
    }

    @Then("(s)he sees the path {string}, {string}, {string}, {string} at {string}")
    public void seesThePath(String first, String second, String third, String fourth, String current) {
        JsonPath request = request();
        assertThat(request.getList("path.name", String.class)).containsExactly(first, second, third, fourth);
        String currentStep = request.getString("currentStep");
        assertThat(request.getString("path.find { it.id == '%s' }.name".formatted(currentStep)))
                .isEqualTo(current);
    }

    @Then("its history shows {string}")
    public void itsHistoryShows(String entry) {
        assertThat(request().getList("history.text", String.class)).contains(entry);
    }

    @Then("(s)he gets the content {person} attached")
    public void getsTheContentAttached(String person) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asByteArray()).isEqualTo(requests.attached(openedFile));
    }

    private JsonPath request() {
        assertThat(response.statusCode()).as("the request opens").isEqualTo(200);
        return response.jsonPath();
    }
}
