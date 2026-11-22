package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import io.github.defiro.engine.internal.Files.StoredFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FilesIT {

    @Autowired
    Files files;

    @Autowired
    Deployment deployment;

    @Autowired
    JdbcClient jdbc;

    final byte[] ticket = "%PDF-1.7 bilhete".getBytes(StandardCharsets.UTF_8);

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
    void aStoredFileOpensWithTheBytesItWasAttachedWith() {
        StoredFile stored = files.store(procedure, null, "bilhete_comboio.pdf", ticket);

        StoredFile found = files.find(stored.id()).orElseThrow();
        assertThat(found).isEqualTo(stored);
        assertThat(found.procedure()).isEqualTo(procedure);
        assertThat(found.filename()).isEqualTo("bilhete_comboio.pdf");
        assertThat(files.content(found)).isEqualTo(ticket);
    }

    @Test
    void aStoredFileKeepsTheSha256OfItsBytes() {
        StoredFile stored = files.store(procedure, null, "bilhete_comboio.pdf", ticket);

        // printf '%PDF-1.7 bilhete' | sha256sum
        assertThat(stored.sha256()).isEqualTo("4b04e77a57fd8f2ed396a8e2da83f23b898d4d8a69ac56c0868311e7994e364a");
    }

    @Test
    void anUnknownFileIsNotFound() {
        assertThat(files.find(UUID.randomUUID())).isEmpty();
    }
}
