package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestPeople;
import io.github.defiro.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SignInIT {

    @Autowired
    SignIn signIn;

    @Autowired
    JdbcClient jdbc;

    TestPeople people;

    @BeforeEach
    void startWithNobody() {
        people = new TestPeople(jdbc);
        people.clear();
    }

    @Test
    void aFirstSignInCreatesThePersonWithNoRolesAndNoGroups() {
        UUID id = signIn.resolve(ana("Ana Ferreira", "ana@cm.test", true));

        assertThat(user(id))
                .containsEntry("name", "Ana Ferreira")
                .containsEntry("email", "ana@cm.test")
                .containsEntry("active", true);
        assertThat(user(id).get("last_login_at")).isNotNull();
        assertThat(rolesOf(id)).isEmpty();
        assertThat(groupsOf(id)).isEmpty();
        assertThat(people.audit()).containsExactly("USER_CREATED", "USER_LINKED");
        assertThat(auditActors()).containsOnly(id);
    }

    @Test
    void signingInAgainIsTheSamePersonEvenWithANewNameAndEmail() {
        UUID id = signIn.resolve(ana("Ana Ferreira", "ana@cm.test", true));

        assertThat(signIn.resolve(ana("Ana Ferreira Lopes", "ana.lopes@cm.test", true)))
                .isEqualTo(id);
        assertThat(user(id)).containsEntry("name", "Ana Ferreira Lopes").containsEntry("email", "ana.lopes@cm.test");
    }

    @Test
    void aVerifiedEmailLinksThePreRegisteredPersonAndKeepsTheirRoles() {
        UUID registered = people.register("Ana F.", "Ana@CM.test");
        people.grant(registered, "WORKFLOW_MANAGER");

        assertThat(signIn.resolve(ana("Ana Ferreira", "ana@cm.test", true))).isEqualTo(registered);
        assertThat(rolesOf(registered)).containsExactly("WORKFLOW_MANAGER");
        assertThat(people.audit()).endsWith("USER_LINKED");
    }

    @Test
    void anUnverifiedEmailLinksNobodyAndIsNotKept() {
        UUID registered = people.register("Ana Ferreira", "ana@cm.test");
        people.grant(registered, "ADMINISTRATOR");

        UUID id = signIn.resolve(ana("Ana Ferreira", "ana@cm.test", false));

        assertThat(id).isNotEqualTo(registered);
        assertThat(user(id)).containsEntry("email", null);
        assertThat(rolesOf(id)).isEmpty();
    }

    @Test
    void aPersonAlreadyLinkedToAnotherAccountIsNotLinkedAgain() {
        UUID first = signIn.resolve(ana("Ana Ferreira", "ana@cm.test", true));

        UUID second =
                signIn.resolve(new IdpAccount("https://idp.test", "another-sub", "Ana Ferreira", "ana@cm.test", true));

        assertThat(second).isNotEqualTo(first);
        assertThat(user(second)).containsEntry("email", null);
    }

    @Test
    void theSameSubjectFromAnotherIssuerIsAnotherPerson() {
        UUID here = signIn.resolve(ana("Ana Ferreira", null, false));

        assertThat(signIn.resolve(new IdpAccount("https://other.test", "idp-sub-ana", "Ana Ferreira", null, false)))
                .isNotEqualTo(here);
    }

    @Test
    void aChangedEmailThatSomeoneElseHasIsIgnored() {
        people.register("Rui Matos", "rui@cm.test");
        UUID id = signIn.resolve(ana("Ana Ferreira", "ana@cm.test", true));

        signIn.resolve(ana("Ana Ferreira", "rui@cm.test", true));

        assertThat(user(id)).containsEntry("email", "ana@cm.test");
    }

    @Test
    void anEmailThatDoesNotFitIsNotKept() {
        UUID id = signIn.resolve(ana("Ana Ferreira", "a".repeat(256) + "@cm.test", true));

        assertThat(user(id)).containsEntry("email", null);
    }

    @Test
    void anInactivePersonIsRefusedAndNothingChanges() {
        UUID id = people.register("Ana Ferreira", "ana@cm.test");
        people.deactivate(id);

        assertThatThrownBy(() -> signIn.resolve(ana("Outro Nome", "ana@cm.test", true)))
                .isInstanceOf(InactiveAccountException.class);
        assertThat(user(id)).containsEntry("name", "Ana Ferreira").containsEntry("last_login_at", null);
        assertThat(identitiesOf(id)).isZero();
    }

    @Test
    void aPersonDeactivatedAfterSigningInIsRefusedToo() {
        UUID id = signIn.resolve(ana("Ana Ferreira", "ana@cm.test", true));
        people.deactivate(id);

        assertThatThrownBy(() -> signIn.resolve(ana("Outro Nome", "ana@cm.test", true)))
                .isInstanceOf(InactiveAccountException.class);
        assertThat(user(id)).containsEntry("name", "Ana Ferreira");
    }

    @ParameterizedTest
    @MethodSource("names")
    void thereIsAlwaysAUsableName(String claim, boolean emailVerified, String kept) {
        assertThat(user(signIn.resolve(ana(claim, "ana@cm.test", emailVerified))))
                .containsEntry("name", kept);
    }

    static Stream<Arguments> names() {
        return Stream.of(
                Arguments.of(null, true, "ana@cm.test"),
                Arguments.of("   ", true, "ana@cm.test"),
                Arguments.of(null, false, "idp-sub-ana"),
                Arguments.of("a".repeat(200), true, "a".repeat(150)),
                // 150 characters are 300 chars in Java: cutting one in half is not a character.
                Arguments.of("a" + "😀".repeat(200), true, "a" + "😀".repeat(149)),
                Arguments.of("Ana", true, "Ana"));
    }

    @Test
    void aTokenWithNoNameKeepsTheNameThePersonHas() {
        UUID id = signIn.resolve(ana("Ana Ferreira", "ana@cm.test", true));

        signIn.resolve(ana(null, "ana@cm.test", true));

        assertThat(user(id)).containsEntry("name", "Ana Ferreira");
    }

    private static IdpAccount ana(String name, String email, boolean emailVerified) {
        return new IdpAccount("https://idp.test", "idp-sub-ana", name, email, emailVerified);
    }

    private Map<String, Object> user(UUID id) {
        return jdbc.sql("select name, email, active, last_login_at from identity.app_user where id = :id")
                .param("id", id)
                .query()
                .singleRow();
    }

    private List<String> rolesOf(UUID id) {
        return jdbc.sql("select role from identity.user_role where user_id = :id")
                .param("id", id)
                .query(String.class)
                .list();
    }

    private List<UUID> groupsOf(UUID id) {
        return jdbc.sql("select group_id from identity.group_member where user_id = :id")
                .param("id", id)
                .query(UUID.class)
                .list();
    }

    private long identitiesOf(UUID id) {
        return jdbc.sql("select count(*) from identity.external_identity where user_id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
    }

    private List<UUID> auditActors() {
        return jdbc.sql("select actor_id from identity.audit_log")
                .query(UUID.class)
                .list();
    }
}
