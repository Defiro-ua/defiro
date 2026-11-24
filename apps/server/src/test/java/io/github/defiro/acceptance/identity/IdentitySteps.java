package io.github.defiro.acceptance.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.defiro.FakeOidcProvider;
import io.github.defiro.acceptance.Browser;
import io.github.defiro.acceptance.Organization;
import io.github.defiro.acceptance.WebBrowser;
import io.restassured.response.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * UC01. Each scenario is one {@link Browser}: it keeps defiro's cookies and follows the sign-in
 * redirects by hand, through {@link FakeOidcProvider}, the way a browser does through the
 * organization's sign-in page.
 */
public class IdentitySteps {

    private static final String INBOX = "/caixa-de-tarefas";
    private static final String HOME = "/";
    private static final Pattern MENU_ITEM = Pattern.compile("«([^»]+)»");
    private static final Map<String, String> MENU_ITEMS = Map.of(
            "Criar workflow", "create-workflow",
            "Novo pedido", "new-request",
            "Caixa de tarefas", "inbox",
            "Workflows", "workflows");
    private static final Map<String, String> NOTICES =
            Map.of("Sessão terminada", "sessao-terminada", "A sua conta está inativa", "conta-inativa");
    private static final Map<String, String> ROLES =
            Map.of("Workflow Manager", "WORKFLOW_MANAGER", "Administrator", "ADMINISTRATOR");

    private final Browser browser = new Browser();

    @Autowired
    Organization organization;

    @Autowired
    JdbcClient jdbc;

    // The port of the server under test, which also serves the web app
    @Value("${local.server.port}")
    int port;

    private WebBrowser webBrowser;
    private String person;
    private String registered;
    private Response lastResponse;

    @ParameterType("[A-Z][\\p{L}]+ [A-Z][\\p{L}]+")
    public String person(String name) {
        return name;
    }

    @Given("these people work in the organization")
    public void thesePeopleWorkInTheOrganization(DataTable table) {
        table.asMaps()
                .forEach(row -> organization.employ(
                        row.get("name"),
                        row.get("unit"),
                        row.get("job title"),
                        row.get("manager"),
                        role(row.get("role"))));
    }

    @Given("{person} has an account at the identity provider, and defiro does not know her yet")
    public void hasAnAccountAndDefiroDoesNotKnowHer(String name) {
        organization.account(name, true);
    }

    @Given("{person} is registered in defiro by her email, as a {string}")
    public void isRegisteredByHerEmail(String name, String role) {
        registered = name;
        organization.people().grant(organization.people().register(name, Organization.email(name)), role(role));
    }

    @Given("the identity provider has verified that email")
    public void theProviderHasVerifiedThatEmail() {
        organization.account(registered, true);
    }

    @Given("the identity provider has not verified that email")
    public void theProviderHasNotVerifiedThatEmail() {
        organization.account(registered, false);
    }

    @Given("{person} is inactive in defiro")
    public void isInactive(String name) {
        organization.people().deactivate(organization.id(name));
    }

    @When("{person} chooses to sign in")
    public void choosesToSignIn(String name) {
        person = name;
        lastResponse = startSignIn();
    }

    @When("{person} signs in")
    public void signsIn(String name) {
        person = name;
        lastResponse = signIn(name);
    }

    @Given("{person} is signed in")
    public void isSignedIn(String name) {
        person = name;
        assertLandsOnInbox(signIn(name));
    }

    @When("IT changes her job title in defiro to {string}")
    public void itChangesHerJobTitle(String jobTitle) {
        jdbc.sql("update identity.app_user set job_title = :jobTitle where id = :id")
                .param("jobTitle", jobTitle)
                .param("id", organization.id(person))
                .update();
    }

    @Given("in defiro {person} belongs to the groups {string} and {string}")
    public void belongsToTheGroups(String name, String group, String otherGroup) {
        UUID id = organization.id(name);
        organization.people().join(id, organization.people().group(group));
        organization.people().join(id, organization.people().group(otherGroup));
    }

    @When("she signs out")
    public void sheSignsOut() {
        lastResponse = signOut();
    }

    @Then("she is sent to the organization's sign-in page")
    public void sheIsSentToTheOrganizationsSignInPage() {
        assertThat(lastResponse.statusCode()).isEqualTo(302);
        URI location = URI.create(lastResponse.header("Location"));
        assertThat(location.getScheme() + "://" + location.getAuthority() + location.getPath())
                .isEqualTo(FakeOidcProvider.instance().authorizationEndpoint());
        assertThat(location.getQuery()).contains("client_id=" + FakeOidcProvider.CLIENT_ID);
    }

    @Then("defiro knows her as {string}, {string} in {string}, managed by {string}")
    public void defiroKnowsHerAs(String name, String jobTitle, String unit, String manager) {
        Response me = profile();
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.jsonPath().getString("name")).isEqualTo(name);
        assertThat(me.jsonPath().getString("jobTitle")).isEqualTo(jobTitle);
        assertThat(me.jsonPath().getString("unit")).isEqualTo(unit);
        assertThat(me.jsonPath().getString("manager")).isEqualTo(manager);
    }

    @Then("defiro knows her job title as {string}")
    public void defiroKnowsHerJobTitleAs(String jobTitle) {
        Response me = profile();
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.jsonPath().getString("jobTitle")).isEqualTo(jobTitle);
    }

    @Then("defiro knows her groups as {string} and {string}")
    public void defiroKnowsHerGroupsAs(String group, String otherGroup) {
        Response me = profile();
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.jsonPath().getList("groups", String.class)).containsExactlyInAnyOrder(group, otherGroup);
    }

    @Then("she belongs to no groups")
    public void sheBelongsToNoGroups() {
        Response me = profile();
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.jsonPath().getList("groups")).isEmpty();
    }

    @Then("she is not signed in to defiro")
    public void sheIsNotSignedIn() {
        assertThat(profile().statusCode()).isEqualTo(401);
    }

    @Then("defiro has stored no password")
    public void defiroHasStoredNoPassword() {
        assertLandsOnInbox(lastResponse);
        List<String> columns = jdbc.sql("""
                select table_schema || '.' || table_name || '.' || column_name
                from information_schema.columns
                where table_schema not in ('pg_catalog', 'information_schema')
                  and (column_name ilike '%pass%' or column_name ilike '%pwd%' or column_name ilike '%secret%')
                """).query(String.class).list();
        assertThat(columns).as("columns that could hold a password").isEmpty();
    }

    @Then("she lands on her task inbox")
    public void sheLandsOnHerTaskInbox() {
        assertLandsOnInbox(lastResponse);
    }

    @Then("^her side menu is (.+)$")
    public void herSideMenuIs(String items) {
        List<String> expected = MENU_ITEM
                .matcher(items)
                .results()
                .map(match -> MENU_ITEMS.get(match.group(1)))
                .toList();
        assertThat(expected).doesNotContainNull().isNotEmpty();
        Response me = profile();
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.jsonPath().getList("menu", String.class)).containsExactlyElementsOf(expected);
    }

    @Then("the top bar shows {string} and {string}")
    public void theTopBarShows(String name, String jobTitle) {
        Response me = profile();
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.jsonPath().getString("name")).isEqualTo(name);
        assertThat(me.jsonPath().getString("jobTitle")).isEqualTo(jobTitle);
    }

    @Then("she is no longer signed in to defiro")
    public void sheIsNoLongerSignedIn() {
        assertThat(lastResponse.statusCode()).isEqualTo(302);
        assertThat(profile().statusCode()).isEqualTo(401);
    }

    @Then("^she is back on the home page with the notice «(.+)»$")
    public void sheIsBackOnTheHomePageWithTheNotice(String text) {
        // The web app shows the notice's text for this marker; the browser journey checks the text.
        String notice = NOTICES.get(text);
        assertThat(notice).isNotNull();
        assertThat(lastResponse.statusCode()).isEqualTo(302);
        URI location = URI.create(lastResponse.header("Location"));
        assertThat(location.getPath()).isEqualTo(HOME);
        assertThat(location.getQuery()).isEqualTo("aviso=" + notice);
    }

    @Given("{person} signed in with the browser")
    public void signedInWithTheBrowser(String name) {
        webBrowser = WebBrowser.open(port);
        webBrowser.signIn(name);
    }

    @When("^she chooses «Terminar sessão» in the account menu$")
    public void sheChoosesToSignOutInTheAccountMenu() {
        button(Pattern.compile("menu da conta")).click();
        button(Pattern.compile("Terminar sessão")).click();
    }

    @Then("^the browser shows the home page with the notice «(.+)»$")
    public void theBrowserShowsTheHomePageWithTheNotice(String text) {
        webBrowser.page().waitForURL(url -> URI.create(url).getPath().equals(HOME));
        PlaywrightAssertions.assertThat(webBrowser.page().getByText(text, new Page.GetByTextOptions().setExact(true)))
                .isVisible();
        assertThat(webBrowser.page().request().get("/api/me").status()).isEqualTo(401);
    }

    @After
    public void closeTheBrowser() {
        if (webBrowser != null) {
            webBrowser.close();
        }
    }

    private Locator button(Pattern name) {
        return webBrowser.page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    private Response startSignIn() {
        return browser.startSignIn();
    }

    private Response signIn(String name) {
        return browser.signIn(name);
    }

    private Response signOut() {
        return browser.signOut();
    }

    private Response profile() {
        return browser.profile();
    }

    private static void assertLandsOnInbox(Response response) {
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(URI.create(response.header("Location")).getPath()).isEqualTo(INBOX);
    }

    private static String role(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        String role = ROLES.get(displayName);
        assertThat(role).as("the role " + displayName).isNotNull();
        return role;
    }
}
