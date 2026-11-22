package io.github.defiro.engine.internal;

import java.time.OffsetDateTime;
import java.util.Map;

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
        Map<String, Object> details = entry.details();
        String who = details.get("name") + ", " + details.get("jobTitle");
        return switch (entry.action()) {
            case "submitted" -> who + ", submeteu o pedido";
            case Decisions.APPROVED -> who + ", aprovou o passo «" + details.get("step") + "»";
            case Decisions.MOVED -> "Passou ao passo «" + details.get("step") + "», com " + details.get("handledBy");
            default -> who + ", " + entry.action();
        };
    }
}
