package io.github.defiro.identity;

import java.util.List;
import org.springframework.security.oauth2.core.ClaimAccessor;

/**
 * Who is signed in, as the other modules need to know them: the stable id of their account (the
 * token's subject), their name, job title and unit, and the object ids of their Entra ID groups. It
 * is read from the token at every request; defiro keeps none of it.
 */
public record Person(String id, String name, String jobTitle, String unit, List<String> groups) {

    public Person {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /** The person of a token: the id token of a browser sign-in, or a bearer token. */
    public static Person of(ClaimAccessor user) {
        return new Person(
                user.getClaimAsString("sub"),
                user.getClaimAsString("name"),
                user.getClaimAsString("jobTitle"),
                user.getClaimAsString("department"),
                // Entra ID leaves the claim out for someone in no group.
                user.getClaimAsStringList("groups"));
    }
}
