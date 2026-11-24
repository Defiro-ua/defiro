package io.github.defiro.acceptance;

import io.cucumber.java.Before;
import io.github.defiro.FakeOidcProvider;
import io.github.defiro.FakeOidcProvider.Account;
import io.github.defiro.TestPeople;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The organization of a scenario: who defiro has registered, and their accounts at the identity
 * provider ({@link FakeOidcProvider}). Each scenario starts with nobody.
 */
public class Organization {

    private final FakeOidcProvider provider = FakeOidcProvider.instance();

    @Autowired
    JdbcClient jdbc;

    @Before(order = 0)
    public void startWithNobody() {
        people().clear();
        provider.directory(List.of());
    }

    public TestPeople people() {
        return new TestPeople(jdbc);
    }

    /** Registers someone in defiro, with their manager, and gives them an account; role may be null. */
    public UUID employ(String name, String unit, String jobTitle, String manager, String role) {
        UUID person = people().register(name, email(name), unit, jobTitle, manager == null ? null : known(manager));
        if (role != null) {
            people().grant(person, role);
        }
        account(name, true);
        return person;
    }

    /** An account at the identity provider, which says nothing of what the person may do. */
    public void account(String name, boolean emailVerified) {
        provider.save(new Account(name, email(name), emailVerified));
    }

    /** Removes the group with this name from defiro. */
    public void remove(String group) {
        jdbc.sql("delete from identity.app_group where name = :name")
                .param("name", group)
                .update();
    }

    /** The id defiro has for this person. */
    public UUID id(String name) {
        return registered(name).orElseThrow();
    }

    /** «Ana Ferreira» is ana.ferreira@cm.test */
    public static String email(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT)
                        .replace(' ', '.')
                + "@cm.test";
    }

    private UUID known(String name) {
        return registered(name).orElseGet(() -> people().register(name, email(name)));
    }

    private Optional<UUID> registered(String name) {
        return jdbc.sql("select id from identity.app_user where lower(email) = :email")
                .param("email", email(name))
                .query(UUID.class)
                .optional();
    }
}
