package io.github.defiro.acceptance.designer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UC04: the questions of the request form. The draft, Sofia's session and the browser are the
 * scenario's own, kept by {@link CreateWorkflowSteps}, as UC04 happens inside the designer of UC03.
 * Steps that read the same as a Given and a Then build what they say before any When, and check it
 * after.
 */
public class FormFieldsSteps {

    @Autowired
    CreateWorkflowSteps designer;

    @When("she opens the request form of the workflow")
    public void sheOpensTheRequestFormOfTheWorkflow() {
        designer.act();
        openTheForm();
    }

    @Then("the form shows {string}, {string} and {string} from the profile, and she cannot change them")
    public void theFormShowsFromTheProfile(String name, String unit, String manager) {
        assertThat(screen().profileRows()).containsText(new String[] {name, unit, manager});
        assertThat(screen().profileRows().first()).containsText("Do perfil (IdP)");
        assertThat(screen().profileRows().getByRole(AriaRole.BUTTON)).hasCount(0);
    }

    @When("she adds the question {string}, a short text that is mandatory")
    public void sheAddsTheQuestion(String label) {
        designer.act();
        designer.requestFields().add(field(label, "short-text", true, List.of()));
        designer.saveDraft();
    }

    @Then("the request form asks {string}, a short text that is mandatory")
    public void theRequestFormAsksAMandatoryShortText(String label) {
        designer.loadDraft()
                .then()
                .body("steps[0].fields.label", contains(label))
                .body("steps[0].fields[0].type", equalTo("short-text"))
                .body("steps[0].fields[0].required", equalTo(true));
    }

    @When("she adds a question to the request form")
    public void sheAddsAQuestionToTheRequestForm() {
        designer.act();
        openTheForm();
        screen().addQuestion();
    }

    @Then("she sees the screen of the question, with its label, its answer type and whether it is mandatory")
    public void sheSeesTheScreenOfTheQuestion() {
        assertThat(screen().question()).containsText("Como é o novo campo?");
        assertThat(screen().questionLabel()).isVisible();
        assertThat(screen().answerTypes()).hasCount(8);
        assertThat(screen().mandatory()).isVisible();
    }

    @Then("she can choose {string}, {string}, {string}, {string}, {string}, {string}, {string} and {string}")
    public void sheCanChoose(String a, String b, String c, String d, String e, String f, String g, String h) {
        for (String type : List.of(a, b, c, d, e, f, g, h)) {
            assertThat(screen().answerType(type)).isVisible();
        }
        assertThat(screen().answerTypes()).hasCount(8);
    }

    @Given("the request form asks {string}, a single choice of {string} only")
    public void theRequestFormAsksASingleChoiceOf(String label, String option) {
        designer.completeThePath();
        designer.requestFields().add(field(label, "single-choice", true, List.of(option)));
        designer.saveDraft();
    }

    @Given("the request form has a question with no label")
    public void theRequestFormHasAQuestionWithNoLabel() {
        designer.requestFields().add(field("", "short-text", false, List.of()));
        designer.saveDraft();
    }

    @Then("the question is flagged with «Falta o rótulo»")
    public void theQuestionIsFlaggedWithNoLabel() {
        assertThat(screen().requesterRows().first()).containsText("Falta o rótulo");
    }

    @Given("the request form asks {string} and then {string}")
    public void theRequestFormAsksAndThen(String first, String second) {
        if (!designer.acted()) {
            designer.requestFields().add(field(first, "short-text", true, List.of()));
            designer.requestFields().add(field(second, "single-choice", true, List.of("Carro próprio", "Comboio")));
            designer.saveDraft();
            openTheForm();
        }
        assertThat(screen().requesterRows()).containsText(new String[] {first, second});
    }

    @When("she moves the question {string} up")
    public void sheMovesTheQuestionUp(String label) {
        designer.act();
        screen().moveUp(label);
    }

    @When("she removes the question {string}")
    public void sheRemovesTheQuestion(String label) {
        designer.act();
        screen().removeQuestion(label);
    }

    @Then("the request form asks {string} only")
    public void theRequestFormAsksOnly(String label) {
        assertThat(screen().requesterRows()).containsText(new String[] {label});
    }

    @Given("the workflow is complete but its request form has no question")
    public void theWorkflowIsCompleteButItsRequestFormHasNoQuestion() {
        designer.completeThePath();
        designer.requestFields().clear();
        designer.saveDraft();
    }

    private void openTheForm() {
        screen().signInAs("Sofia Almeida");
        screen().openDraft(designer.draftId(), "campos");
    }

    private DesignerScreen screen() {
        return designer.screen();
    }

    static Map<String, Object> field(String label, String type, boolean required, List<String> options) {
        Map<String, Object> field = new HashMap<>();
        field.put("id", UUID.randomUUID().toString());
        field.put("label", label);
        field.put("type", type);
        field.put("required", required);
        field.put("options", options);
        return field;
    }
}
