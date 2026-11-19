package io.github.defiro.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.github.defiro.FakeEntraId;

/**
 * A real browser (Chromium, through Playwright) on the web app, for the {@code @browser} scenarios.
 * Chromium starts once for the whole run; each scenario gets a new window with no cookies.
 */
public final class WebBrowser implements AutoCloseable {

    private static Browser chromium;

    private final BrowserContext context;
    private final Page page;

    private WebBrowser(int port) {
        assertThat(WebApp.built())
                .as("the web app is built in %s: run npm run build in apps/web", WebApp.BUILD)
                .isTrue();
        context = chromium().newContext(new Browser.NewContextOptions().setBaseURL("http://localhost:" + port));
        context.setDefaultTimeout(15_000);
        page = context.newPage();
    }

    /** A new window on the web app that the server under test serves on this port */
    public static WebBrowser open(int port) {
        return new WebBrowser(port);
    }

    private static synchronized Browser chromium() {
        if (chromium == null) {
            Playwright playwright = Playwright.create();
            chromium = playwright.chromium().launch();
            Runtime.getRuntime().addShutdownHook(new Thread(playwright::close));
        }
        return chromium;
    }

    public Page page() {
        return page;
    }

    /** Signs in as UC01 does: «Entrar», the organization's sign-in page, and back on the inbox. */
    public void signIn(String name) {
        FakeEntraId.instance().signInAs(name);
        page.navigate("/");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Entrar"))
                .click();
        page.waitForURL("**/caixa-de-tarefas");
    }

    @Override
    public void close() {
        context.close();
    }
}
