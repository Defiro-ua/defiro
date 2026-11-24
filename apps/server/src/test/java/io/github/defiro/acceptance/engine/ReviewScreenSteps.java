package io.github.defiro.acceptance.engine;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.defiro.acceptance.WebBrowser;
import java.util.List;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * The {@code @browser} scenarios of UC17 and UC19, in a real browser on the web app: the reviewer signs in
 * as in UC01, lands on the inbox, and reviews from there. Everything is found by its role and
 * accessible name, as a screen reader finds it. The request is the Background's, through the API.
 */
public class ReviewScreenSteps {

    private WebBrowser browser;
    private Page page;
    private int clicks;
    private boolean confirmationAsked;

    @Autowired
    ReviewSteps review;

    @Autowired
    RequestSteps requests;

    // The port of the server under test, which also serves the web app
    @Value("${local.server.port}")
    int port;

    @Before("(@UC17 or @UC18 or @UC19) and @browser")
    public void openTheBrowser() {
        browser = WebBrowser.open(port);
        page = browser.page();
        // A confirmation would be the browser's own dialog or one of the web app's
        page.onDialog(dialog -> {
            confirmationAsked = true;
            dialog.dismiss();
        });
    }

    @After("(@UC17 or @UC18 or @UC19) and @browser")
    public void closeTheBrowser() {
        browser.close();
    }

    @When("{person} reviews the request {string}")
    public void reviewsTheRequest(String person, String reference) {
        browser.signIn(person);
        rever(reference).click();
        decision().waitFor();
    }

    @When("{person} approves the request {string} from his inbox")
    public void approvesFromTheInbox(String person, String reference) {
        browser.signIn(person);
        clicks = 0;
        click(rever(reference));
        click(button("Aprovar"));
        page.waitForURL("**/caixa-de-tarefas");
    }

    @When("{person} approves the request {string} from his inbox, after seeing what changed")
    public void approvesAfterSeeingWhatChanged(String person, String reference) {
        // Still signed in, since they returned it
        page.navigate("/caixa-de-tarefas");
        rever(reference).click();
        assertThat(rowOf("Regresso")).containsText("09/11/2026 19:10");
        assertThat(rowOf("Regresso")).containsText("Alterado");
        assertThat(rowOf("Regresso")).containsText("Antes: 10/11/2026 19:40");
        assertThat(page.getByText("Novo na versão 2")).isVisible();
        button("Aprovar").click();
    }

    @Then("he is back on his inbox with the notice «{}»")
    public void heIsBackOnHisInbox(String text) {
        assertThat(page).hasURL(Pattern.compile(".*/caixa-de-tarefas$"));
        assertThat(notice(text)).isVisible();
    }

    @Then("next to {string} she reads «{}»")
    public void nextToSheReads(String label, String text) {
        assertThat(rowOf(label)).containsText(text);
    }

    // An answer of «Dados do pedido», with all that is next to it
    private Locator rowOf(String label) {
        return page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Dados do pedido"))
                .locator("dl > div")
                .filter(new Locator.FilterOptions()
                        .setHas(page.locator("dt").getByText(label, new Locator.GetByTextOptions().setExact(true))));
    }

    @Given("{person} approved the request {string} from his inbox")
    public void approvedFromTheInbox(String person, String reference) {
        approvesFromTheInbox(person, reference);
        assertThat(notice("Pedido aprovado")).isVisible();
    }

    @When("{person} chooses «Ver» on it in her inbox")
    public void choosesVerInHerInbox(String person) {
        browser.signIn(person);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("^Ver\\s*o pedido ")))
                .click();
        page.waitForURL("**/pedidos/**");
    }

    @Then("her screen shows «{}», with no decision")
    public void herScreenShows(String text) {
        assertThat(page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Estado")))
                .containsText(text);
        assertThat(decision()).hasCount(0);
        assertThat(button("Aprovar")).hasCount(0);
    }

    // UC19: the notes, on the request the reviewer took, opened from the inbox

    @When("he reviews the request {string}")
    public void heReviewsTheRequest(String reference) {
        reviewsTheRequest(review.taker(), reference);
    }

    @When("on his screen {person} leaves the note {string} on {string}")
    public void onHisScreenLeavesTheNote(String person, String text, String label) {
        reviewsTheRequest(person, requests.reference());
        noteButton(label).click();
        noteBox(label).fill(text);
        button("Guardar nota").click();
    }

    @When("on his screen {person} changes his note on {string} to {string}")
    public void onHisScreenChangesHisNote(String person, String label, String text) {
        onHisScreenLeavesTheNote(person, text, label);
    }

    @When("on his screen {person} deletes his note on {string}, confirming")
    public void onHisScreenDeletesHisNote(String person, String label) {
        choosesApagar(person, label);
        button("Apagar a nota").click();
    }

    @When("{person} chooses «Apagar» on his note on {string}")
    public void choosesApagar(String person, String label) {
        reviewsTheRequest(person, requests.reference());
        page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(Pattern.compile("^Apagar\\s*a nota sobre " + label + "$")))
                .click();
    }

    @When("on his screen {person} saves an empty note on {string}")
    public void onHisScreenSavesAnEmptyNote(String person, String label) {
        reviewsTheRequest(person, requests.reference());
        noteButton(label).click();
        button("Guardar nota").click();
    }

    @When("{person} types {int} characters in the note on {string}")
    public void typesCharactersInTheNote(String person, int length, String label) {
        reviewsTheRequest(person, requests.reference());
        noteButton(label).click();
        // Key by key, as a person types: the box itself stops at its limit
        noteBox(label).pressSequentially("x".repeat(length));
    }

    @Then("his decision reads «{}»")
    public void hisDecisionReads(String text) {
        assertThat(page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("A sua decisão")))
                .containsText(text);
    }

    @Then("he sees the notice «{}»")
    public void heSeesTheNotice(String text) {
        assertThat(notice(text)).isVisible();
    }

    @Then("he sees «{}» next to the note and in a notice")
    public void heSeesNextToTheNoteAndInANotice(String text) {
        assertThat(page.getByRole(AriaRole.ALERT)).containsText(text);
        assertThat(notice(text)).isVisible();
    }

    @Then("the note stays open for him to write or cancel")
    public void theNoteStaysOpen() {
        assertThat(page.getByRole(AriaRole.TEXTBOX)).isVisible();
        assertThat(button("Cancelar")).isVisible();
    }

    @Then("the box keeps {int} of them, with «{}»")
    public void theBoxKeeps(int length, String counter) {
        assertThat(page.getByRole(AriaRole.TEXTBOX)).hasValue("x".repeat(length));
        assertThat(page.getByText(counter, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
    }

    @Then("he is asked «{}», with «Apagar a nota» and «Cancelar»")
    public void heIsAsked(String question) {
        assertThat(page.getByText(question, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(button("Apagar a nota")).isVisible();
        assertThat(button("Cancelar")).isVisible();
    }

    private Locator noteButton(String label) {
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("^(Nota|Editar nota)\\s*sobre " + label + "$")));
    }

    private Locator noteBox(String label) {
        return page.getByRole(
                AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(Pattern.compile("^Nota sobre «" + label + "»")));
    }

    @When("{int} seconds go by")
    public void secondsGoBy(int seconds) {
        page.waitForTimeout(seconds * 1000.0);
    }

    @When("he chooses «Desfazer»")
    public void heChoosesDesfazer() {
        button("Desfazer").click();
    }

    @Then("^his decision buttons are «([^»]+)», «([^»]+)» and «([^»]+)», with «([^»]+)» as the main one$")
    public void hisDecisionButtonsAre(String first, String second, String last, String main) {
        List<String> expected = List.of(first, second, last);
        Locator buttons = decision().getByRole(AriaRole.BUTTON);
        Assertions.assertThat(
                        buttons.allInnerTexts().stream().map(String::strip).toList())
                .containsExactlyElementsOf(expected);
        // The main button is the one in the brand's colour; the others are secondary
        for (int k = 0; k < buttons.count(); k++) {
            boolean isMain = expected.get(k).equals(main);
            Assertions.assertThat(buttons.nth(k).getAttribute("class").contains("bg-brand"))
                    .as("«%s» is the main button", expected.get(k))
                    .isEqualTo(isMain);
        }
    }

    @Then("it took him {int} clicks, with no confirmation asked")
    public void itTookHimClicks(int expected) {
        Assertions.assertThat(clicks).isEqualTo(expected);
        Assertions.assertThat(confirmationAsked).as("a confirmation was asked").isFalse();
        assertThat(page.getByRole(AriaRole.DIALOG)).hasCount(0);
        assertThat(notice("Pedido aprovado")).isVisible();
    }

    @Then("his inbox still shows the notice «{}» with «Desfazer»")
    public void hisInboxStillShowsTheNotice(String text) {
        assertThat(page).hasURL(Pattern.compile(".*/caixa-de-tarefas$"));
        assertThat(notice(text)).isVisible();
        assertThat(button("Desfazer")).isVisible();
    }

    @Then("he is reviewing the request {string} again, with the notice «{}»")
    public void heIsReviewingTheRequestAgain(String reference, String text) {
        assertThat(page).hasURL(Pattern.compile(".*/pedidos/" + reference + "$"));
        assertThat(notice(text)).isVisible();
        assertThat(button("Aprovar")).isEnabled();
    }

    // «Rever» in the inbox: it takes the task and opens the request
    private Locator rever(String reference) {
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("^Rever\\s*o pedido " + reference + " ")));
    }

    // For the other screen steps of the review stories (RejectScreenSteps)

    Page page() {
        return page;
    }

    void signIn(String person) {
        browser.signIn(person);
    }

    private void click(Locator target) {
        target.click();
        clicks++;
    }

    private Locator decision() {
        return page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("Decisão"));
    }

    // The notice's title; what follows it is read out to screen readers only
    Locator notice(String text) {
        // Playwright runs the pattern in the browser, as JavaScript, which has no \Q…\E quoting
        String escaped = text.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
        return page.getByRole(AriaRole.STATUS).getByText(Pattern.compile("^" + escaped));
    }

    private Locator button(String name) {
        return page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }
}
