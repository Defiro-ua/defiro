package io.github.defiro.engine.internal;

import io.github.defiro.identity.Person;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** TEMPORARY, for UC27 (Daniel): see {@link Inbox}. */
@RestController
class InboxController {

    private final Inbox inbox;

    InboxController(Inbox inbox) {
        this.inbox = inbox;
    }

    @GetMapping("/api/engine/inbox")
    List<Inbox.Item> inbox(@AuthenticationPrincipal ClaimAccessor user) {
        return inbox.of(Person.of(user));
    }
}
