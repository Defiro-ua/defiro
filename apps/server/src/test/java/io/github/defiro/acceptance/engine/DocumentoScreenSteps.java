package io.github.defiro.acceptance.engine;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.github.defiro.acceptance.WebBrowser;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;

/**
 * The {@code @browser} scenarios of UC23, in a real browser on the web app: the requester signs in as
 * in UC01 and finds her requests on the inbox, under the tasks. Everything is found by its role and
 * accessible name, as a screen reader finds it. The requests are the Background's, through the API.
 */
public class DocumentoScreenSteps {

    private WebBrowser browser;
    private Page page;
    private String reference;

    // The port of the server under test, which also serves the web app
    @Value("${local.server.port}")
    int port;

    @Before("@UC23 and @browser")
    public void openTheBrowser() {
        browser = WebBrowser.open(port);
        page = browser.page();
    }

    @After("@UC23 and @browser")
    public void closeTheBrowser() {
        browser.close();
    }

    @Quando("{person} abre «Os meus pedidos»")
    public void abreOsMeusPedidos(String person) {
        browser.signIn(person);
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Os meus pedidos")))
                .isVisible();
    }

    @Então("o pedido {string} mostra {string}")
    public void oPedidoMostra(String reference, String text) {
        this.reference = reference;
        assertThat(row(reference)).containsText(text);
    }

    @E("oferece «Documento»")
    public void oferece() {
        assertThat(documento(reference)).isVisible();
    }

    @Então("o pedido {string} não oferece «Documento»")
    public void naoOferece(String reference) {
        // The row is there, so that nothing is missing only because the list has not loaded
        assertThat(row(reference)).isVisible();
        assertThat(documento(reference)).hasCount(0);
    }

    // The row of the request, found by its reference
    private Locator row(String reference) {
        return page.getByRole(AriaRole.ROW)
                .filter(new Locator.FilterOptions()
                        .setHas(page.getByText(reference, new Page.GetByTextOptions().setExact(true))));
    }

    private Locator documento(String reference) {
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("^Documento\\s*do pedido " + reference + "$")));
    }
}
