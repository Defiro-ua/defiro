package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestPeople;
import io.github.defiro.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class IdentitySchemaIT {

    @Autowired
    JdbcClient jdbc;

    TestPeople people;

    @BeforeEach
    void startWithNobody() {
        people = new TestPeople(jdbc);
        people.clear();
    }

    @Test
    void twoPeopleCannotShareAnEmailWhateverTheCase() {
        people.register("Ana Ferreira", "ana@cm.test");

        assertThatThrownBy(() -> people.register("Outra Ana", "ANA@cm.test")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void severalPeopleMayHaveNoEmail() {
        people.register("Ana Ferreira", null);

        assertThatCode(() -> people.register("Rui Matos", null)).doesNotThrowAnyException();
    }

    @Test
    void twoGroupsCannotShareAName() {
        // The designer keeps a group by its name.
        people.group("Chefias");

        assertThatThrownBy(() -> jdbc.sql("insert into identity.app_group (name) values ('Chefias')")
                        .update())
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void onlyTheTwoRolesExist() {
        UUID ana = people.register("Ana Ferreira", null);

        assertThatThrownBy(() -> people.grant(ana, "WorkflowManager"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void everyChangeOfAccessIsInTheAuditLog() {
        UUID ana = people.register("Ana Ferreira", null);
        UUID finance = people.group("Divisão Financeira");
        people.grant(ana, "ADMINISTRATOR");
        people.join(ana, finance);
        people.leave(ana, finance);
        people.revoke(ana, "ADMINISTRATOR");
        people.deactivate(ana);
        people.deactivate(ana); // already inactive: not a second deactivation

        assertThat(people.audit())
                .containsExactly(
                        "USER_CREATED",
                        "ROLE_GRANTED",
                        "GROUP_MEMBER_ADDED",
                        "GROUP_MEMBER_REMOVED",
                        "ROLE_REVOKED",
                        "USER_DEACTIVATED");
    }

    @Test
    void theAuditLogIsAppendOnly() {
        people.register("Ana Ferreira", null);

        assertThatThrownBy(() -> jdbc.sql("delete from identity.audit_log").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() ->
                        jdbc.sql("update identity.audit_log set action = 'X'").update())
                .hasMessageContaining("append-only");
    }
}
