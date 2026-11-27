package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.engine.internal.GeneratedDocuments.Document;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GeneratedDocumentsIT {

    @Autowired
    GeneratedDocuments documents;

    @Autowired
    Deployment deployment;

    @Autowired
    JdbcClient jdbc;

    final byte[] pdf = "%PDF-1.7 ordem de reembolso".getBytes(StandardCharsets.UTF_8);

    UUID procedure;

    @BeforeEach
    void aProcedureIsRunning() {
        UUID draft = UUID.randomUUID();
        int version = deployment.deploy(
                SpecificationVersionsIT.specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1));
        procedure = UUID.randomUUID();
        jdbc.sql("""
                        insert into engine.procedure (id, reference, draft, version, requester_id, current_step_id, status)
                        values (:id, :reference, :draft, :version, :requester, 'pedido', 'running')""")
                .param("id", procedure)
                .param("reference", procedure.toString().substring(0, 20))
                .param("draft", draft)
                .param("version", version)
                .param("requester", UUID.randomUUID().toString())
                .update();
    }

    @Test
    void storesTheDocumentAndItsBytes() {
        documents.store(procedure, "ordem_reembolso_AC-2026-0098.pdf", "application/pdf", pdf);

        Document found = documents.of(procedure).orElseThrow();
        assertThat(found.procedure()).isEqualTo(procedure);
        assertThat(found.filename()).isEqualTo("ordem_reembolso_AC-2026-0098.pdf");
        assertThat(found.mediaType()).isEqualTo("application/pdf");
        assertThat(found.sha256()).isEqualTo(Digests.sha256(pdf));
        assertThat(documents.content(found)).isEqualTo(pdf);
    }

    @Test
    void aProcedureHasOneDocument() {
        documents.store(procedure, "ordem_reembolso_AC-2026-0098.pdf", "application/pdf", pdf);

        assertThatThrownBy(() -> documents.store(procedure, "outra.pdf", "application/pdf", pdf))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void noDocumentYet() {
        assertThat(documents.of(UUID.randomUUID())).isEmpty();
    }
}
