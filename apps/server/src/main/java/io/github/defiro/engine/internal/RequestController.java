package io.github.defiro.engine.internal;

import io.github.defiro.identity.Person;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A request, by its reference, and its files: only for the people who take part in it. */
@RestController
@RequestMapping("/api/engine/procedures/{reference}")
class RequestController {

    private final Requests requests;

    RequestController(Requests requests) {
        this.requests = requests;
    }

    @GetMapping
    ResponseEntity<Requests.Request> request(
            @PathVariable String reference, @AuthenticationPrincipal ClaimAccessor user) {
        return ResponseEntity.of(requests.open(reference, Person.of(user)));
    }

    /** The file as it was attached, to open in the browser. */
    @GetMapping("/files/{id}")
    ResponseEntity<byte[]> file(
            @PathVariable String reference, @PathVariable UUID id, @AuthenticationPrincipal ClaimAccessor user) {
        return requests.file(reference, id, Person.of(user))
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaTypeFactory.getMediaType(file.filename())
                                .orElse(MediaType.APPLICATION_OCTET_STREAM))
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.inline()
                                        .filename(file.filename())
                                        .build()
                                        .toString())
                        .body(requests.content(file)))
                .orElse(ResponseEntity.notFound().build());
    }
}
