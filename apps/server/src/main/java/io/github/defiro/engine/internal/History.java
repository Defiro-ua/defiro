package io.github.defiro.engine.internal;

import java.time.OffsetDateTime;

/**
 * The history of a request as people read it, one sentence per entry, with the name and job title
 * the person had at the time. The texts are the prototype's. The history screen is UC25 (Bernardo).
 */
final class History {

    private History() {}

    record Line(OffsetDateTime at, String text) {}

    static Line of(AuditLog.Entry entry) {
        return new Line(entry.at(), text(entry));
    }

    private static String text(AuditLog.Entry entry) {
        String who = entry.details().get("name") + ", " + entry.details().get("jobTitle");
        return switch (entry.action()) {
            case "submitted" -> who + ", submeteu o pedido";
            default -> who + ", " + entry.action();
        };
    }
}
