package io.github.defiro.acceptance.designer;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.github.defiro.acceptance.WebBrowser;
import java.util.regex.Pattern;

/**
 * The web app as the Workflow Manager sees it in the browser: the side menu and the «Criar workflow»
 * designer. Everything is found by its role and accessible name, as a screen reader finds it.
 */
final class DesignerScreen implements AutoCloseable {

    private final WebBrowser browser;
    private final Page page;
    private boolean signedIn;

    DesignerScreen(int port) {
        browser = WebBrowser.open(port);
        page = browser.page();
    }

    void signInAs(String name) {
        if (!signedIn) {
            browser.signIn(name);
            signedIn = true;
        }
    }

    /** The links of the side menu, in order, without the skip link */
    Locator menu() {
        return page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Menu principal"))
                .locator("a:not(.skip-link)");
    }

    void openMenuItem(String item) {
        menu().getByText(item, new Locator.GetByTextOptions().setExact(true)).click();
    }

    /** Opens a draft in the designer, on a given screen (such as percurso), or on its first one */
    void openDraft(String id, String screen) {
        page.navigate("/workflows/criar/" + id + (screen == null ? "" : "?ecra=" + screen));
        question().waitFor();
    }

    /** The question of the screen that is open */
    Locator question() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(2));
    }

    Locator kicker(String text) {
        return page.getByText(text, new Page.GetByTextOptions().setExact(true));
    }

    Locator workflowName() {
        return page.getByLabel(Pattern.compile("^Nome do workflow"));
    }

    void goOn() {
        page.locator("#btn-continuar").click();
    }

    void goBack() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Anterior"))
                .click();
    }

    void moveUp(String step) {
        button("Mover «" + step + "» para cima").click();
    }

    void remove(String step) {
        button("Remover o passo «" + step + "»").click();
    }

    /** Adds the final validation, if the path offers it: once there is one, it no longer does */
    void addFinalValidation() {
        Locator add = page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^Validação final")));
        if (add.count() > 0) {
            add.click();
        }
    }

    /** The steps of the path: on the path screen, or «O percurso até agora» beside a step's screen */
    Locator path() {
        return page.locator("ol[aria-label='Passos do percurso'] h3, aside li");
    }

    private Locator button(String name) {
        return page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    @Override
    public void close() {
        browser.close();
    }
}
