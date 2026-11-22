package io.github.defiro.acceptance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.defiro.acceptance.AccessSteps;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The review stories (UC17, UC18, UC19) through the Engine API, as the web app calls it. Each person
 * signs in through Entra ID, as in UC01, in their own browser. Each scenario gets a new instance.
 */
public class ReviewSteps {

    private static final String INBOX = "/api/engine/inbox";
    private static final String PROCEDURES = "/api/engine/procedures/";

    private final People people = new People();
    // The task of each step, by the step's name, as the request reaches it.
    private final Map<String, UUID> tasks = new HashMap<>();
    private Response response;
    private String openedFile;
    private String reference;
    private Instant actedFrom;
    private Instant actedTo;
    // The scenario's first approval, which «his approval» means.
    private String approver;
    private UUID approval;

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

    @When("{person} approves the request {string}")
    public void approvesTheRequest(String person, String reference) {
        approve(person, reference, requests.waitingAt());
    }

    @Given("{person} approved the request {string}")
    public void approvedTheRequest(String person, String reference) {
        approve(person, reference, requests.waitingAt());
        assertThat(response.statusCode()).as(person + " approves").isEqualTo(200);
    }

    @Given("{person} approved it at {string}")
    public void approvedItAt(String person, String step) {
        approve(person, reference, step);
        assertThat(response.statusCode()).as(person + " approves").isEqualTo(200);
    }

    @When("he undoes his approval")
    public void heUndoesHisApproval() {
        undoesHisApproval(approver);
    }

    @When("{person} undoes his approval")
    public void undoesHisApproval(String person) {
        assertThat(approval).as("an approval to undo").isNotNull();
        actedFrom = Instant.now();
        response = people.of(person).request().post("/api/engine/tasks/" + approval + "/undo");
        actedTo = Instant.now();
        access.answered(response);
    }

    @Then("the request is back at {string}, in the inbox of {person}")
    public void theRequestIsBackAt(String step, String person) {
        assertThat(response.statusCode()).as("the approval is undone").isEqualTo(200);
        theRequestIsAt(step, person);
    }

    @Then("it is no longer in the inbox of {person}")
    public void itIsNoLongerInTheInboxOf(String person) {
        assertThat(inboxOf(person)).doesNotContain(reference);
    }

    @Then("it is back in the inbox of {person}")
    public void itIsBackInTheInboxOf(String person) {
        assertThat(inboxOf(person)).contains(reference);
    }

    // Nothing is erased from the history
    @Then("its history still shows {string}")
    public void itsHistoryStillShows(String entry) {
        assertThat(history()).contains(entry);
    }

    @Then("the newest entry of its history is {string}, with the date and time of the undoing")
    public void theNewestEntryOfItsHistoryIs(String entry) {
        theNewestEntriesAre(List.of(entry));
    }

    @Then("the request is at {string}, in the inbox of {person}")
    public void theRequestIsAt(String step, String person) {
        assertThat(currentStep()).isEqualTo(step);
        assertThat(inboxOf(person)).contains(reference);
    }

    @Then("the request is still at {string}")
    public void theRequestIsStillAt(String step) {
        assertThat(currentStep()).isEqualTo(step);
    }

    @Then("it is no longer in the inbox of {person} or {person}")
    public void itIsNoLongerInTheInboxOf(String person, String otherPerson) {
        assertThat(inboxOf(person)).doesNotContain(reference);
        assertThat(inboxOf(otherPerson)).doesNotContain(reference);
    }

    @Then("^(?:s?he) is told «(.+)»$")
    public void isTold(String text) {
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.jsonPath().getString("detail")).isEqualTo(text);
    }

    @Then("the request was approved only once")
    public void theRequestWasApprovedOnlyOnce() {
        String approval = "aprovou o passo «" + requests.waitingAt() + "»";
        assertThat(history().stream().filter(line -> line.endsWith(approval))).hasSize(1);
    }

    @Then("the newest entries of its history, with the date and time of the approval, are")
    public void theNewestEntriesOfItsHistoryAre(DataTable entries) {
        theNewestEntriesAre(
                entries.asMaps().stream().map(row -> row.get("entry")).toList());
    }

    // The newest first, each at the time of the scenario's When.
    private void theNewestEntriesAre(List<String> expected) {
        JsonPath request = requestAs(requests.requester());
        List<String> texts = request.getList("history.text", String.class);
        List<String> times = request.getList("history.at", String.class);
        int newest = texts.size() - 1;
        for (int k = 0; k < expected.size(); k++) {
            assertThat(texts.get(newest - k)).isEqualTo(expected.get(k));
            assertThat(OffsetDateTime.parse(times.get(newest - k)).toInstant())
                    .as("the date and time of " + expected.get(k))
                    .isBetween(actedFrom.minusSeconds(1), actedTo.plusSeconds(1));
        }
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

    @Then("{string} is marked as changed, with the answer of version {int}, {string}, before it")
    public void isMarkedAsChanged(String field, int version, String previous) {
        JsonPath request = request();
        assertThat(request.getInt("version")).isEqualTo(version + 1);
        assertThat(request.getBoolean(answer(field) + ".changed"))
                .as(field + " is changed")
                .isTrue();
        assertThat(request.getString(answer(field) + ".previous")).isEqualTo(previous);
    }

    @Then("{string} is not marked as changed")
    public void isNotMarkedAsChanged(String field) {
        assertThat(request().getBoolean(answer(field) + ".changed"))
                .as(field + " is changed")
                .isFalse();
    }

    @Then("the file {string} is marked as new in version {int}")
    public void theFileIsMarkedAsNewIn(String filename, int version) {
        JsonPath request = request();
        assertThat(request.getInt("version")).isEqualTo(version);
        assertThat(request.getInt(file(filename) + ".version")).isEqualTo(version);
    }

    @Then("the file {string} is not")
    public void theFileIsNot(String filename) {
        JsonPath request = request();
        assertThat(request.getInt(file(filename) + ".version")).isLessThan(request.getInt("version"));
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

    private void approve(String person, String reference, String step) {
        this.reference = reference;
        actedFrom = Instant.now();
        response = people.of(person).request().post("/api/engine/tasks/" + taskOf(step) + "/approve");
        actedTo = Instant.now();
        access.answered(response);
        if (response.statusCode() == 200 && approval == null) {
            approver = person;
            approval = taskOf(step);
        }
        if (response.statusCode() == 200 && response.jsonPath().getString("nextStep") != null) {
            tasks.put(
                    response.jsonPath().getString("nextStep"),
                    UUID.fromString(response.jsonPath().getString("nextTask")));
        }
    }

    private UUID taskOf(String step) {
        if (tasks.isEmpty()) {
            tasks.put(requests.waitingAt(), requests.task());
        }
        UUID task = tasks.get(step);
        assertThat(task).as("the task of the step " + step).isNotNull();
        return task;
    }

    // The request as its requester sees it: they always take part in it.
    private JsonPath requestAs(String person) {
        Response request = people.of(person).request().get(PROCEDURES + reference);
        assertThat(request.statusCode()).isEqualTo(200);
        return request.jsonPath();
    }

    private String currentStep() {
        JsonPath request = requestAs(requests.requester());
        String id = request.getString("currentStep");
        return request.getString("path.find { it.id == '%s' }.name".formatted(id));
    }

    private List<String> history() {
        return requestAs(requests.requester()).getList("history.text", String.class);
    }

    private List<String> inboxOf(String person) {
        Response inbox = people.of(person).request().get(INBOX);
        assertThat(inbox.statusCode()).isEqualTo(200);
        return inbox.jsonPath().getList("reference", String.class);
    }

    private static String answer(String label) {
        return "answers.find { it.label == '%s' }".formatted(label);
    }

    private static String file(String filename) {
        return "files.find { it.filename == '%s' }".formatted(filename);
    }

    private JsonPath request() {
        assertThat(response.statusCode()).as("the request opens").isEqualTo(200);
        return response.jsonPath();
    }
}
