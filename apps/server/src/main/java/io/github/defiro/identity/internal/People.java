package io.github.defiro.identity.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code identity} schema: people, the accounts they sign in with, their roles and groups. */
@Repository
class People {

    private final JdbcClient jdbc;

    People(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Until the end of this transaction, the audit log records this person as whoever acts. */
    void actAs(UUID person) {
        jdbc.sql("select set_config('defiro.actor', :person, true)")
                .param("person", person.toString())
                .query(String.class)
                .single();
    }

    Optional<UUID> linkedTo(String issuer, String subject) {
        return jdbc.sql("select user_id from identity.external_identity where issuer = :issuer and subject = :subject")
                .param("issuer", issuer)
                .param("subject", subject)
                .query(UUID.class)
                .optional();
    }

    /**
     * The person registered in advance with this email, if they never signed in. Two statements: the
     * first waits for anyone else linking the same person, and only the second then sees their link.
     */
    Optional<UUID> registeredAndNotLinked(String email) {
        return jdbc.sql("select id from identity.app_user where lower(email) = lower(:email) for update")
                .param("email", email)
                .query(UUID.class)
                .optional()
                .filter(person -> !isLinked(person));
    }

    private boolean isLinked(UUID person) {
        return jdbc.sql("select exists (select from identity.external_identity where user_id = :person)")
                .param("person", person)
                .query(Boolean.class)
                .single();
    }

    boolean isActive(UUID person) {
        return jdbc.sql("select active from identity.app_user where id = :person")
                .param("person", person)
                .query(Boolean.class)
                .single();
    }

    void create(UUID person, String name) {
        jdbc.sql("insert into identity.app_user (id, name) values (:person, :name)")
                .param("person", person)
                .param("name", name)
                .update();
    }

    void link(UUID person, String issuer, String subject) {
        jdbc.sql("insert into identity.external_identity (user_id, issuer, subject)"
                        + " values (:person, :issuer, :subject)")
                .param("person", person)
                .param("issuer", issuer)
                .param("subject", subject)
                .update();
    }

    /** Null keeps the name or the email they have; so does an email that is someone else's. */
    void signedIn(UUID person, String name, String email) {
        jdbc.sql("""
                        update identity.app_user set
                            name = coalesce(cast(:name as text), name),
                            email = case
                                when exists (select from identity.app_user other
                                             where lower(other.email) = lower(cast(:email as text))
                                               and other.id <> :person) then email
                                else coalesce(cast(:email as text), email)
                            end,
                            last_login_at = now()
                        where id = :person""")
                .param("person", person)
                .param("name", name)
                .param("email", email)
                .update();
    }

    Optional<Profile> profile(UUID person) {
        return jdbc.sql("""
                        select p.name, p.department, p.job_title, m.name as manager,
                               array(select role from identity.user_role where user_id = p.id) as roles,
                               array(select g.name
                                     from identity.group_member gm
                                     join identity.app_group g on g.id = gm.group_id
                                     where gm.user_id = p.id
                                     order by g.name collate "pt-x-icu") as groups
                        from identity.app_user p
                        left join identity.app_user m on m.id = p.manager_id
                        where p.id = :person""")
                .param("person", person)
                .query((row, number) -> new Profile(
                        row.getString("name"),
                        row.getString("department"),
                        row.getString("job_title"),
                        row.getString("manager"),
                        names(row, "groups"),
                        Profile.menuFor(Set.copyOf(names(row, "roles")))))
                .optional();
    }

    Access access(UUID person) {
        return jdbc.sql("""
                        select active, array(select role from identity.user_role where user_id = p.id) as roles
                        from identity.app_user p
                        where p.id = :person""")
                .param("person", person)
                .query((row, number) -> new Access(row.getBoolean("active"), Set.copyOf(names(row, "roles"))))
                .optional()
                .orElse(Access.NONE);
    }

    private static List<String> names(ResultSet row, String column) throws SQLException {
        return List.of((String[]) row.getArray(column).getArray());
    }
}
