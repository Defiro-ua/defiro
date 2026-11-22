package io.github.defiro.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.ClaimAccessor;

class PersonTest {

    @Test
    void aPersonIsWhatTheTokenSays() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "ana-ferreira");
        claims.put("name", "Ana Ferreira");
        claims.put("jobTitle", "Técnica Superior");
        claims.put("department", "Divisão de Obras Municipais");
        claims.put("groups", List.of("8f1c2a5e-0000-4000-8000-000000000001"));

        Person person = Person.of(token(claims));

        assertThat(person)
                .isEqualTo(new Person(
                        "ana-ferreira",
                        "Ana Ferreira",
                        "Técnica Superior",
                        "Divisão de Obras Municipais",
                        List.of("8f1c2a5e-0000-4000-8000-000000000001")));
    }

    @Test
    void someoneInNoGroupHasNoGroups() {
        // Entra ID leaves the groups claim out for someone in no group.
        Person person = Person.of(token(Map.of("sub", "ana-ferreira", "name", "Ana Ferreira")));

        assertThat(person.groups()).isEmpty();
    }

    private static ClaimAccessor token(Map<String, Object> claims) {
        return () -> claims;
    }
}
