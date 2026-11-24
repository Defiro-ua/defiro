package io.github.defiro;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** People, roles and groups in the {@code identity} schema, as the organization's IT keeps them in SQL. */
public final class TestPeople {

    private final JdbcClient jdbc;

    public TestPeople(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID register(String name, String email) {
        return register(name, email, null, null, null);
    }

    public UUID register(String name, String email, String department, String jobTitle, UUID manager) {
        return jdbc.sql("""
                        insert into identity.app_user (name, email, department, job_title, manager_id)
                        values (:name, :email, :department, :jobTitle, :manager)
                        returning id""")
                .param("name", name)
                .param("email", email)
                .param("department", department)
                .param("jobTitle", jobTitle)
                .param("manager", manager)
                .query(UUID.class)
                .single();
    }

    public void grant(UUID person, String role) {
        jdbc.sql("insert into identity.user_role (user_id, role) values (:person, :role)")
                .param("person", person)
                .param("role", role)
                .update();
    }

    public void revoke(UUID person, String role) {
        jdbc.sql("delete from identity.user_role where user_id = :person and role = :role")
                .param("person", person)
                .param("role", role)
                .update();
    }

    /** The group with this name, created if there is none. */
    public UUID group(String name) {
        return jdbc.sql("select id from identity.app_group where name = :name")
                .param("name", name)
                .query(UUID.class)
                .optional()
                .orElseGet(() -> jdbc.sql("insert into identity.app_group (name) values (:name) returning id")
                        .param("name", name)
                        .query(UUID.class)
                        .single());
    }

    public void join(UUID person, UUID group) {
        jdbc.sql("insert into identity.group_member (group_id, user_id) values (:group, :person)")
                .param("group", group)
                .param("person", person)
                .update();
    }

    public void leave(UUID person, UUID group) {
        jdbc.sql("delete from identity.group_member where group_id = :group and user_id = :person")
                .param("group", group)
                .param("person", person)
                .update();
    }

    public void deactivate(UUID person) {
        jdbc.sql("update identity.app_user set active = false where id = :person")
                .param("person", person)
                .update();
    }

    /** What the audit log recorded, oldest first. */
    public List<String> audit() {
        return jdbc.sql("select action from identity.audit_log order by id")
                .query(String.class)
                .list();
    }

    /** Empties every table that has people or groups in it. Truncating is the only way past the append-only log. */
    public void clear() {
        jdbc.sql("truncate identity.app_user, identity.app_group cascade").update();
    }

    /** A request from someone signed in: defiro knows them only by their id. */
    public static RequestPostProcessor signedInAs(UUID person) {
        return oidcLogin().idToken(token -> token.subject(person.toString()));
    }
}
