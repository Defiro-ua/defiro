package io.github.defiro.designer.internal;

import static io.github.defiro.TestTokens.TRUSTED_KEY;
import static io.github.defiro.TestTokens.bearer;
import static io.github.defiro.designer.internal.WorkflowDraftTest.complete;
import static io.github.defiro.designer.internal.WorkflowDraftTest.withSteps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.defiro.TestTokens;
import io.github.defiro.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

// «Workflows» shows «Versão N · criado por <name> a <date>»: the name comes from the token of
// whoever published, at the moment they published.
@Import({TestcontainersConfiguration.class, TestTokens.TrustedIssuer.class})
@SpringBootTest
@AutoConfigureMockMvc
class DraftPublicationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    Drafts drafts;

    @Test
    void aPublishedDraftShowsWhoPublishedItAndWhen() throws Exception {
        UUID id = drafts.add(withSteps(complete("Validação da chefia"))).id();
        String sofia = bearer(
                TRUSTED_KEY,
                claims -> claims.subject("0f3e-sofia")
                        .claim("name", "Sofia Almeida")
                        .claim("roles", List.of("WorkflowManager")));

        mvc.perform(post("/api/designer/drafts/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, sofia))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/designer/drafts/" + id).header(HttpHeaders.AUTHORIZATION, sofia))
                .andExpect(jsonPath("$.publishedVersion").value(1))
                .andExpect(jsonPath("$.publishedBy").value("Sofia Almeida"))
                .andExpect(jsonPath("$.publishedAt").value(notNullValue()));
        assertThat(drafts.find(id).orElseThrow().published().by()).isEqualTo("0f3e-sofia");
    }
}
