package io.github.defiro.identity.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who in defiro an account of the identity provider is: the person it is linked to; else the person
 * registered in advance with its verified email; else a new person, with no roles and no groups.
 */
@Service
class SignIn {

    // As wide as their columns, in characters
    private static final int NAME = 150;
    private static final int EMAIL = 255;

    private final People people;

    SignIn(People people) {
        this.people = people;
    }

    @Transactional
    UUID resolve(IdpAccount account) {
        String email = account.emailVerified() ? orNull(account.email()) : null;
        String name = orNull(account.name());

        Optional<UUID> linked = people.linkedTo(account.issuer(), account.subject());
        Optional<UUID> known = linked.isPresent() || email == null ? linked : people.registeredAndNotLinked(email);
        if (known.isPresent() && !people.isActive(known.get())) {
            throw new InactiveAccountException();
        }

        UUID person = known.orElseGet(UUID::randomUUID);
        people.actAs(person);
        if (known.isEmpty()) {
            people.create(person, fit(firstOf(name, email, account.subject()), NAME));
        }
        if (linked.isEmpty()) {
            people.link(person, account.issuer(), account.subject());
        }
        people.signedIn(person, name == null ? null : fit(name, NAME), email != null && fits(email) ? email : null);
        return person;
    }

    private static String firstOf(String... candidates) {
        return Stream.of(candidates).filter(Objects::nonNull).findFirst().orElseThrow();
    }

    private static String fit(String text, int characters) {
        return text.codePointCount(0, text.length()) <= characters
                ? text
                : text.substring(0, text.offsetByCodePoints(0, characters));
    }

    private static boolean fits(String email) {
        return email.codePointCount(0, email.length()) <= EMAIL;
    }

    private static String orNull(String claim) {
        return claim == null || claim.isBlank() ? null : claim.strip();
    }
}
