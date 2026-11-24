package io.github.defiro.acceptance.engine;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@code @browser} scenarios of UC18, on the window ReviewScreenSteps opens: the menus of the
 * two decisions, the notice, and what the requester sees afterwards.
 */
public class RejectScreenSteps {

    private static final String RETURN = "Devolver para correção";
    private static final String REJECT = "Rejeitar o pedido";

    // The decision whose menu is open
    private String button;

    @Autowired
    ReviewScreenSteps screen;

    @Given("{person} opened «{}» on the request {string}")
    public void opened(String person, String button, String reference) {
        screen.reviewsTheRequest(person, reference);
        open(button);
    }

    @When("{person} opens «{}» on the request {string}")
    public void opens(String person, String button, String reference) {
        opened(person, button, reference);
    }

    @Then("he can open «{}» and «{}», one at a time")
    public void heCanOpenBoth(String first, String second) {
        open(first);
        assertThat(menu(first)).isVisible();
        open(second);
        assertThat(menu(second)).isVisible();
        assertThat(menu(first)).hasCount(0);
    }

    @Then("he can tick {string}, and only those")
    public void heCanTick(String reasons) {
        List<String> expected = RequestSteps.reasons(reasons);
        assertThat(checkboxes()).hasCount(expected.size());
        for (String reason : expected) {
            tick(reason);
        }
        Assertions.assertThat(checkboxes().all()).allMatch(Locator::isChecked);
    }

    @When("he confirms it with no reason")
    public void heConfirmsIt() {
        confirm().click();
    }

    @When("he confirms it for {string} with no message")
    public void heConfirmsItFor(String reason) {
        tick(reason);
        confirm().click();
    }

    @When("he confirms it for {string} with the message {string}")
    public void heConfirmsItWith(String reason, String message) {
        tick(reason);
        message().fill(message);
        confirm().click();
    }

    @When("she ticks {string}")
    public void sheTicks(String reason) {
        tick(reason);
    }

    @Then("the message reads «{}»")
    public void theMessageReads(String text) {
        assertThat(message()).hasValue(text);
    }

    @Then("he is asked for the reasons and the message, and for no signature")
    public void heIsAskedForNoSignature() {
        // Everything there is to fill in or press in the menu
        Locator controls = menu(button).locator("input, textarea, select, button, a");
        assertThat(controls).hasCount(checkboxes().count() + 3);
        assertThat(message()).hasCount(1);
        assertThat(confirm()).hasCount(1);
        assertThat(menu(button).getByText("Cancelar")).hasCount(1);
        assertThat(menu(button)).not().containsText(Pattern.compile("assin", Pattern.CASE_INSENSITIVE));
    }

    @Then("he sees the error «{}»")
    public void heSeesTheError(String text) {
        assertThat(menu(button).getByText(text, new Locator.GetByTextOptions().setExact(true)))
                .isVisible();
    }

    @Then("the confirm button is red")
    public void theConfirmButtonIsRed() {
        assertThat(confirm()).hasClass(Pattern.compile("\\bbg-error\\b"));
    }

    @When("on his screen {person} returns the request {string} for {string} with the message {string}")
    public void onHisScreenReturns(String person, String reference, String reasons, String message) {
        decide(person, reference, RETURN, reasons, message);
    }

    @Given("on his screen {person} returned the request {string} for {string} with the message {string}")
    public void onHisScreenReturned(String person, String reference, String reasons, String message) {
        decide(person, reference, RETURN, reasons, message);
    }

    @Given("on his screen {person} rejected the request {string} for {string} with the message {string}")
    public void onHisScreenRejected(String person, String reference, String reasons, String message) {
        decide(person, reference, REJECT, reasons, message);
    }

    // The journey of UC17, all on the reviewer's screen

    @Given("{person} returned the request {string} for {string}, with the note {string} on {string}")
    public void returnedWithANote(String person, String reference, String reasons, String note, String label) {
        screen.onHisScreenLeavesTheNote(person, note, label);
        screen.heSeesTheNotice("Nota guardada");
        open(RETURN);
        RequestSteps.reasons(reasons).forEach(this::tick);
        confirm().click();
        screen.heSeesTheNotice("Pedido devolvido para correção");
    }

    @Then("the notice shows «{}»")
    public void theNoticeShows(String text) {
        Locator shown = screen.notice(text);
        assertThat(shown).isVisible();
        // Seen by everyone, not only read out to screen readers, which Playwright also calls visible
        assertThat(shown).not().hasClass(Pattern.compile("\\bsr-only\\b"));
    }

    @When("{person} opens her request {string}")
    public void opensHerRequest(String person, String reference) {
        screen.signIn(person);
        screen.page().navigate("/pedidos/" + reference);
    }

    @Then("she reads «{}», with {string} and {string}")
    public void sheReads(String title, String reason, String message) {
        Locator why = screen.page()
                .getByRole(
                        AriaRole.REGION,
                        new Page.GetByRoleOptions().setName(title).setExact(true));
        assertThat(why.getByRole(AriaRole.LISTITEM)).hasText(reason);
        assertThat(why.getByText("«" + message + "»")).isVisible();
    }

    @Then("«Estado» reads «{}»")
    public void estadoReads(String text) {
        Locator state = screen.page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Estado"));
        assertThat(state.getByText(text, new Locator.GetByTextOptions().setExact(true)))
                .isVisible();
    }

    private void decide(String person, String reference, String button, String reasons, String message) {
        opened(person, button, reference);
        RequestSteps.reasons(reasons).forEach(this::tick);
        message().fill(message);
        confirm().click();
        screen.page().waitForURL("**/caixa-de-tarefas");
    }

    // The button that opens the menu; inside it, the one that confirms has the same name
    private void open(String button) {
        this.button = button;
        screen.page()
                .getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName(button)
                                .setExact(true)
                                .setExpanded(false))
                .click();
    }

    private Locator menu(String button) {
        return screen.page()
                .getByRole(
                        AriaRole.REGION,
                        new Page.GetByRoleOptions().setName(button).setExact(true));
    }

    private Locator checkboxes() {
        return menu(button).getByRole(AriaRole.CHECKBOX);
    }

    private void tick(String reason) {
        menu(button)
                .getByRole(
                        AriaRole.CHECKBOX,
                        new Locator.GetByRoleOptions().setName(reason).setExact(true))
                .check();
    }

    private Locator message() {
        return menu(button).getByRole(AriaRole.TEXTBOX);
    }

    private Locator confirm() {
        return menu(button)
                .getByRole(
                        AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(button).setExact(true));
    }
}
