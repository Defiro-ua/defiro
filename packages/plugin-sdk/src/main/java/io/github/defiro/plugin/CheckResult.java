package io.github.defiro.plugin;

/**
 * The result of an automatic check a step shows, for example at final validation (UC22).
 *
 * @param name what was checked, as the person reads it
 * @param passed whether the check passed
 * @param message what is wrong when it did not pass, as the person reads it
 */
public record CheckResult(String name, boolean passed, String message) {

    public CheckResult {
        Require.text(name, "A check needs its name");
    }
}
