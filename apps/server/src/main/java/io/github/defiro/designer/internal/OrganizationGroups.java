package io.github.defiro.designer.internal;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The organization's groups, read from the {@code identity} schema. The Designer never writes there. */
@Repository
class OrganizationGroups {

    private final JdbcClient jdbc;

    OrganizationGroups(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The names of the groups, in alphabetical order as in Portuguese: «Águas» comes before «Chefias» */
    List<String> names() {
        return jdbc.sql("select name from identity.app_group order by name collate \"pt-x-icu\"")
                .query(String.class)
                .list();
    }
}
