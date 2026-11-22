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
import org.springframework.beans.factory.annotation.Value;

/**
 * The {@code @browser} scenarios of UC17, in a real browser on the web app: the reviewer signs in
 * as in UC01, lands on the inbox, and reviews from there. Everything is found by its role and
 * accessible name, as a screen reader finds it. The request is the Background's, through the API.
 */
public class ReviewScreenSteps {

    private WebBrowser browser;
    private Page page;
    private int clicks;
    private boolean confirmationAsked;

    // The port of the server under test, which also serves the web app
    @Value("${local.server.port}")
    int port;

    @Before("@UC17 and @browser")
    public void openTheBrowser() {
        browser = WebBrowser.open(port);
        page = browser.page();
        // A confirmation would be the browser's own dialog or one of the web app's
        page.onDialog(dialog -> {
            confirmationAsked = true;
            dialog.dismiss();
        });
    }

    @After("@UC17 and @browser")
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

    private void click(Locator target) {
        target.click();
        clicks++;
    }

    private Locator decision() {
        return page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("Decisão"));
    }

    // The notice's title; what follows it is read out to screen readers only
    private Locator notice(String text) {
        // Playwright runs the pattern in the browser, as JavaScript, which has no \Q…\E quoting
        String escaped = text.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
        return page.getByRole(AriaRole.STATUS).getByText(Pattern.compile("^" + escaped));
    }

    private Locator button(String name) {
        return page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }
}
