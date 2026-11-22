package io.github.defiro.acceptance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.acceptance.Browser;
import java.util.HashMap;
import java.util.Map;

/** The people of a scenario, each in their own browser, signed in through Entra ID on first use. */
final class People {

    private final Map<String, Browser> browsers = new HashMap<>();

    Browser of(String name) {
        return browsers.computeIfAbsent(name, person -> {
            Browser browser = new Browser();
            assertThat(browser.signIn(person).statusCode())
                    .as(person + " signs in")
                    .isEqualTo(302);
            return browser;
        });
    }
}
