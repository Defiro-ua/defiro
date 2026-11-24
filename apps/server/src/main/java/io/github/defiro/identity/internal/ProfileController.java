package io.github.defiro.identity.internal;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ProfileController {

    private final People people;

    ProfileController(People people) {
        this.people = people;
    }

    @GetMapping("/api/me")
    Profile me(Authentication authentication) {
        return people.profile(UUID.fromString(authentication.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
