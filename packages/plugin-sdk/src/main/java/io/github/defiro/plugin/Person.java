package io.github.defiro.plugin;

/**
 * Someone taking part in a procedure, as defiro knows them. Only the id is always there: the rest
 * of the profile may be missing.
 *
 * @param id the person's internal code in defiro, never their identity-provider account
 * @param unit the organizational unit they work in
 * @param manager who their manager is
 */
public record Person(String id, String name, String jobTitle, String unit, String manager) {

    public Person {
        Require.text(id, "A person needs their id");
    }
}
