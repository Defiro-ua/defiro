package io.github.defiro.acceptance.engine;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ViewportSize;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
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
    // The file the browser was given by «Transferir PDF», or straight away where it cannot show a PDF
    private Download download;
    // Who the scenario is about, for the steps that say «ela»
    private String person;

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

    @Dado("que o ecrã de {person} tem {int} píxeis de largura")
    public void oEcraTem(String person, int width) {
        this.person = person;
        page.setViewportSize(width, 800);
    }

    @Quando("ela abre o documento de {string} e desce até ao fim")
    public void abreEDesce(String reference) {
        abreODocumento(person, reference);
        assertThat(dialog().locator("iframe")).isVisible();
        // The dialog's box is what scrolls, taller than the screen; the bar is sticky at its top
        Locator box = dialog().locator("> div");
        box.evaluate("box => box.scrollTo(0, box.scrollHeight)");
        Assertions.assertThat(((Number) box.evaluate("box => box.scrollTop")).doubleValue())
                .as("the dialog's box scrolled")
                .isGreaterThan(0);
    }

    @Então("continua a ver «Fechar» e «Transferir PDF»")
    public void continuaAVer() {
        ViewportSize viewport = page.viewportSize();
        for (Locator button : List.of(fechar(), transferir())) {
            assertThat(button).isVisible();
            BoundingBox box = button.boundingBox();
            Assertions.assertThat(box.x).isGreaterThanOrEqualTo(0);
            Assertions.assertThat(box.y).isGreaterThanOrEqualTo(0);
            Assertions.assertThat(box.x + box.width).isLessThanOrEqualTo(viewport.width);
            Assertions.assertThat(box.y + box.height).isLessThanOrEqualTo(viewport.height);
        }
    }

    @Dado("que {person} abriu o documento de {string} em «Os meus pedidos»")
    public void abriuODocumento(String person, String reference) {
        abreODocumento(person, reference);
        assertThat(dialog()).isVisible();
    }

    @Quando("escolhe «Fechar»")
    public void escolheFechar() {
        fechar().click();
    }

    @Então("volta a «Os meus pedidos», em «Documento» de {string}")
    public void voltaADocumento(String reference) {
        assertThat(dialog()).hasCount(0);
        assertThat(documento(reference)).isFocused();
        Assertions.assertThat(page.evaluate(
                        "() => document.activeElement?.closest('section')?.querySelector('h2')?.textContent ?? null"))
                .as("the heading of the section that has the focus")
                .isEqualTo("Os meus pedidos");
    }

    @Dado("que {person} abriu o documento de {string}")
    public void abriuODocumentoSemMais(String person, String reference) {
        abriuODocumento(person, reference);
    }

    @Quando("escolhe «Transferir PDF»")
    public void escolheTransferir() {
        download = page.waitForDownload(() -> transferir().click());
    }

    @Dado("que o navegador de {person} não consegue mostrar um PDF no ecrã")
    public void semVisorDePdf(String person) {
        this.person = person;
        // Before the page loads, so that the app sees it from its first line
        page.addInitScript("Object.defineProperty(navigator,'pdfViewerEnabled',{get:()=>false})");
    }

    @Quando("ela abre o documento de {string} em «Os meus pedidos»")
    public void elaAbreODocumento(String reference) {
        // No dialog opens here: the PDF goes straight to a download
        browser.signIn(person);
        download = page.waitForDownload(() -> documento(reference).click());
    }

    @Então("{string} é transferido")
    public void eTransferido(String filename) {
        Assertions.assertThat(download).as("a download started").isNotNull();
        Assertions.assertThat(download.suggestedFilename()).isEqualTo(filename);
    }

    @E("é-lhe dito que foi guardado")
    public void foiGuardado() {
        // The notice's title; its longer text is for screen readers and follows in the same line
        assertThat(page.getByRole(AriaRole.STATUS).getByText("PDF guardado")).isVisible();
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
