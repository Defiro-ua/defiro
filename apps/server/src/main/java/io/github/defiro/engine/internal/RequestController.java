package io.github.defiro.engine.internal;

import io.github.defiro.identity.Person;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
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

/**
 * A request, by its reference, its files and, once closed, its final document: only for the people
 * who take part in it.
 */
@RestController
@RequestMapping("/api/engine/procedures/{reference}")
class RequestController {

    // The files a browser may show on defiro's own address. Anything else, such as HTML or SVG,
    // could run a script there with the reviewer's session, so it is only downloaded.
    private static final Set<MediaType> SHOWN =
            Set.of(MediaType.APPLICATION_PDF, MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG, MediaType.IMAGE_GIF);

    private final Requests requests;
    private final FinalDocuments finalDocuments;

    RequestController(Requests requests, FinalDocuments finalDocuments) {
        this.requests = requests;
        this.finalDocuments = finalDocuments;
    }

    @GetMapping
    ResponseEntity<Requests.Request> request(
            @PathVariable String reference, @AuthenticationPrincipal ClaimAccessor user) {
        return ResponseEntity.of(requests.open(reference, Person.of(user)));
    }

    /**
     * The file as it was attached: opened in the browser when it is a PDF or an image, downloaded
     * otherwise. Either way the browser runs nothing in it (Content-Security-Policy: sandbox).
     */
    @GetMapping("/files/{id}")
    ResponseEntity<byte[]> file(
            @PathVariable String reference, @PathVariable UUID id, @AuthenticationPrincipal ClaimAccessor user) {
        return requests.file(reference, id, Person.of(user))
                .map(file -> {
                    Optional<MediaType> shown =
                            MediaTypeFactory.getMediaType(file.filename()).filter(SHOWN::contains);
                    ContentDisposition disposition = (shown.isPresent()
                                    ? ContentDisposition.inline()
                                    : ContentDisposition.attachment())
                            .filename(file.filename(), StandardCharsets.UTF_8)
                            .build();
                    return ResponseEntity.ok()
                            .contentType(shown.orElse(MediaType.APPLICATION_OCTET_STREAM))
                            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                            .header("Content-Security-Policy", "sandbox")
                            .body(requests.content(file));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * The final document, as it was generated at closing: opened in the browser, a PDF of defiro's
     * own making. No cache keeps it, since only the participants may have it.
     */
    @GetMapping("/document")
    ResponseEntity<byte[]> document(@PathVariable String reference, @AuthenticationPrincipal ClaimAccessor user) {
        return finalDocuments
                .open(reference, Person.of(user))
                .map(opened -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(opened.document().mediaType()))
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.inline()
                                        .filename(opened.document().filename(), StandardCharsets.UTF_8)
                                        .build()
                                        .toString())
                        .cacheControl(CacheControl.noStore())
                        .body(opened.content()))
                .orElse(ResponseEntity.notFound().build());
    }
}
