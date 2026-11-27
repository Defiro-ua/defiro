package io.github.defiro.engine.internal;

import io.github.defiro.identity.Person;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** «Os meus pedidos» (UC23): the requests the person signed in has made. */
@RestController
class MyRequestsController {

    private final Procedures procedures;

    MyRequestsController(Procedures procedures) {
        this.procedures = procedures;
    }

    @GetMapping("/api/engine/my-requests")
    List<Procedures.Mine> myRequests(@AuthenticationPrincipal ClaimAccessor user) {
        return procedures.of(Person.of(user).id());
    }
}
