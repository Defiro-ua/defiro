package io.github.defiro.acceptance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.defiro.acceptance.AccessSteps;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UC19 through the Engine API, as the web app calls it: the notes of the reviewer who took the
 * task, on the task of the step the request waits at (ReviewSteps). A note is on a field of the
 * request, by its label, or on one of its files.
 */
public class NoteSteps {

    private Response response;
    private String writer;

    @Autowired
    ReviewSteps review;

    @Autowired
    RequestSteps requests;

    @Autowired
    AccessSteps access;

    @When("{person} leaves the note {string} on {string}")
    public void leavesTheNote(String person, String text, String label) {
        put(person, RequestSteps.fieldOf(label), text);
    }

    @Given("{person} left the note {string} on {string}")
    public void leftTheNote(String person, String text, String label) {
        leavesTheNote(person, text, label);
        assertThat(response.statusCode()).as(person + " leaves the note").isEqualTo(200);
    }

    @When("{person} leaves the note {string} on the file {string}")
    public void leavesTheNoteOnTheFile(String person, String text, String filename) {
        put(person, fileField(filename), text);
    }

    @When("{person} leaves a note of {int} characters on {string}")
    public void leavesANoteOfCharacters(String person, int length, String label) {
        put(person, RequestSteps.fieldOf(label), "x".repeat(length));
    }

    @When("{person} changes his note on {string} to {string}")
    public void changesHisNote(String person, String label, String text) {
        put(person, RequestSteps.fieldOf(label), text);
    }

    @When("{person} deletes his note on {string}")
    public void deletesHisNote(String person, String label) {
        writer = person;
        response = review.browserOf(person).request().delete(notes() + RequestSteps.fieldOf(label));
        access.answered(response);
    }

    @Then("his note on {string} reads {string}")
    public void hisNoteReads(String label, String text) {
        assertThat(response.statusCode()).as("the note is saved").isEqualTo(200);
        assertThat(myNote(RequestSteps.fieldOf(label))).containsEntry("text", text);
    }

    @Then("his note on the file {string} reads {string}")
    public void hisNoteOnTheFileReads(String filename, String text) {
        assertThat(response.statusCode()).as("the note is saved").isEqualTo(200);
        assertThat(myNote(fileField(filename))).containsEntry("text", text).containsEntry("label", filename);
    }

    @Then("the note is refused")
    public void theNoteIsRefused() {
        assertThat(response.statusCode()).isEqualTo(422);
    }

    @Then("{string} has no note of his")
    public void hasNoNoteOfHis(String label) {
        assertThat(myNotes(reviewer()))
                .extracting(note -> note.get("field"))
                .doesNotContain(RequestSteps.fieldOf(label));
    }

    @Then("(s)he sees no note on {string}")
    public void seesNoNoteOn(String label) {
        assertThat(review.lastResponse().statusCode()).as("the request opens").isEqualTo(200);
        JsonPath seen = review.lastResponse().jsonPath();
        String field = RequestSteps.fieldOf(label);
        assertThat(seen.getList("notes.field", String.class)).doesNotContain(field);
        assertThat(seen.getList("notesFrom.field", String.class)).doesNotContain(field);
    }

    @Then("she sees next to {string} «{}»")
    public void seesNextTo(String label, String note) {
        assertThat(review.lastResponse().statusCode()).isEqualTo(200);
        List<Map<String, Object>> from = review.lastResponse().jsonPath().getList("notesFrom");
        assertThat(from)
                .filteredOn(seen -> RequestSteps.fieldOf(label).equals(seen.get("field")))
                .extracting(seen -> "Nota de " + seen.get("author") + ": " + seen.get("text"))
                .containsExactly(note);
    }

    @Then("his note on {string} can be changed again")
    public void hisNoteCanBeChangedAgain(String label) {
        put(
                reviewer(),
                RequestSteps.fieldOf(label),
                "Falta o certificado de presença com o carimbo da entidade formadora.");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Then("{person} no longer sees it")
    public void noLongerSeesIt(String person) {
        Response seen = review.browserOf(person).request().get("/api/engine/procedures/" + requests.reference());
        // With the approval undone she may no longer open the request at all, nor its notes
        if (seen.statusCode() != 403) {
            assertThat(seen.statusCode()).isEqualTo(200);
            assertThat(seen.jsonPath().getList("notesFrom")).isEmpty();
        }
    }

    private void put(String person, String field, String text) {
        writer = person;
        response = review.browserOf(person)
                .request()
                .contentType(ContentType.JSON)
                .body(Map.of("text", text))
                .put(notes() + field);
        access.answered(response);
    }

    // The notes of the task the request waits at
    private String notes() {
        return "/api/engine/tasks/" + review.waitingTask() + "/notes/";
    }

    // Who the notes are of: who wrote last, or who took the task
    private String reviewer() {
        return writer != null ? writer : review.taker();
    }

    private Map<String, Object> myNote(String field) {
        return myNotes(reviewer()).stream()
                .filter(note -> field.equals(note.get("field")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no note of " + reviewer() + " on " + field));
    }

    private List<Map<String, Object>> myNotes(String person) {
        return review.requestSeenBy(person).getList("notes");
    }

    private String fileField(String filename) {
        return "file:" + requests.fileId(filename);
    }
}
