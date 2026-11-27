package io.github.defiro.acceptance.engine;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.github.defiro.acceptance.WebBrowser;
import java.util.List;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
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
    // The server's answer to the dialog's request for the document
    private Response document;

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

    @Quando("{person} abre o documento de {string} em «Os meus pedidos»")
    public void abreODocumento(String person, String reference) {
        abreOsMeusPedidos(person);
        document = page.waitForResponse(
                response -> response.url().endsWith("/procedures/" + reference + "/document"),
                () -> documento(reference).click());
    }

    @Então("vê no ecrã o PDF {string}")
    public void veOPdf(String filename) {
        Locator frame = dialog().locator("iframe[title='" + filename + "']");
        assertThat(frame).isVisible();
        Assertions.assertThat(document.status()).isEqualTo(200);
        // The PDF loads in the frame itself, and is not sandboxed away (Review Focus 1)
        Frame pdf = frame.elementHandle().contentFrame();
        page.waitForCondition(() -> pdf.url().startsWith("blob:"));
    }

    @E("vê «Fechar» e «Transferir PDF» no topo")
    public void veABarraNoTopo() {
        BoundingBox pdf = dialog().locator("iframe").boundingBox();
        for (Locator button : List.of(fechar(), transferir())) {
            assertThat(button).isVisible();
            Assertions.assertThat(button.boundingBox().y).isLessThan(pdf.y);
        }
    }

    private Locator dialog() {
        return page.getByRole(AriaRole.DIALOG);
    }

    private Locator fechar() {
        return dialog().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("^Fechar")));
    }

    private Locator transferir() {
        return dialog().getByRole(
                        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("^Transferir PDF")));
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
