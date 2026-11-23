package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestcontainersConfiguration;
import io.github.defiro.engine.deployment.Deployment;
import io.github.defiro.engine.deployment.Publication;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The rules the {@code engine} schema keeps by itself for procedures, the versions of their data,
 * their tasks and their history, whatever code writes to it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProcedureTablesIT {

    static final String ISSUER = "https://login.example.org/defiro";

    @Autowired
    Deployment deployment;

    @Autowired
    JdbcClient jdbc;

    final UUID draft = UUID.randomUUID();
    final UUID procedure = UUID.randomUUID();

    @BeforeEach
    void deployAWorkflow() {
        deployment.deploy(
                SpecificationVersionsIT.specification("Ajudas de custo"), new Publication("sofia.almeida", draft, 1));
    }

    @Test
    void aProcedureStartsOnADeployedVersion() {
        startProcedure(procedure, draft, 1);

        assertThat(jdbc.sql("select state from engine.procedure where id = :id")
                        .param("id", procedure)
                        .query(String.class)
                        .single())
                .isEqualTo("started");
    }

    @Test
    void aProcedureCannotStartOnAVersionThatWasNeverDeployed() {
        assertThatThrownBy(() -> startProcedure(procedure, draft, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoProceduresCannotHaveTheSameReference() {
        startProcedure(procedure, draft, 1);
        UUID other = UUID.randomUUID();
        startProcedure(other, draft, 1);
        setReference(procedure, "AC-2026-0147");

        assertThatThrownBy(() -> setReference(other, "AC-2026-0147"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aProcedureIsInOneOfTheKnownStates() {
        startProcedure(procedure, draft, 1);

        assertThatThrownBy(() -> jdbc.sql("update engine.procedure set state = 'lost' where id = :id")
                        .param("id", procedure)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSubmittedVersionOfTheRequestCannotBeChanged() {
        startProcedure(procedure, draft, 1);
        submitData(procedure, 1);

        assertThatThrownBy(() -> jdbc.sql(
                                "update engine.request_data set answers = '{\"destino\": \"Porto\"}' where procedure = :id")
                        .param("id", procedure)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("nunca muda");
    }

    @Test
    void aSubmittedVersionOfTheRequestCannotBeDeleted() {
        startProcedure(procedure, draft, 1);
        submitData(procedure, 1);

        assertThatThrownBy(() -> jdbc.sql("delete from engine.request_data where procedure = :id")
                        .param("id", procedure)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("nunca muda");
    }

    @Test
    void eachResubmissionIsANewVersionOfTheRequest() {
        startProcedure(procedure, draft, 1);
        submitData(procedure, 1);
        submitData(procedure, 2);

        assertThat(jdbc.sql("select count(*) from engine.request_data where procedure = :id")
                        .param("id", procedure)
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
        assertThatThrownBy(() -> submitData(procedure, 2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTaskIsCompletedWithItsOutcomeAndWhoActed() {
        startProcedure(procedure, draft, 1);
        UUID task = createTask(procedure);

        int completed = jdbc.sql("""
                        update engine.task
                        set completed_at = now(), outcome = 'approved',
                            completed_by_issuer = :issuer, completed_by_subject = 'rui-matos',
                            completed_by_name = 'Rui Matos', completed_by_job_title = 'Chefe de Divisão'
                        where id = :id and completed_at is null""").param("issuer", ISSUER).param("id", task).update();

        assertThat(completed).isEqualTo(1);
    }

    @Test
    void aTaskCannotBeHalfCompleted() {
        startProcedure(procedure, draft, 1);
        UUID task = createTask(procedure);

        assertThatThrownBy(() -> jdbc.sql("update engine.task set completed_at = now() where id = :id")
                        .param("id", task)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anEntryOfTheHistoryCannotBeChanged() {
        startProcedure(procedure, draft, 1);
        record(procedure, "submitted");

        assertThatThrownBy(() -> jdbc.sql("update engine.audit_entry set action = 'closed' where procedure = :id")
                        .param("id", procedure)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("nunca muda");
    }

    @Test
    void anEntryOfTheHistoryCannotBeDeleted() {
        startProcedure(procedure, draft, 1);
        record(procedure, "submitted");

        assertThatThrownBy(() -> jdbc.sql("delete from engine.audit_entry where procedure = :id")
                        .param("id", procedure)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("nunca muda");
    }

    @Test
    void theHistoryOnlyRecordsKnownActions() {
        startProcedure(procedure, draft, 1);

        assertThatThrownBy(() -> record(procedure, "deleted")).isInstanceOf(DataIntegrityViolationException.class);
    }

    void startProcedure(UUID id, UUID draft, int version) {
        jdbc.sql("""
                        insert into engine.procedure
                            (id, draft, version, current_step, requester_issuer, requester_subject,
                             requester_name, requester_job_title)
                        values (:id, :draft, :version, 'pedido', :issuer, 'ana-ferreira',
                                'Ana Ferreira', 'Técnica Superior')""")
                .param("id", id)
                .param("draft", draft)
                .param("version", version)
                .param("issuer", ISSUER)
                .update();
    }

    void setReference(UUID id, String reference) {
        jdbc.sql("update engine.procedure set reference = :reference where id = :id")
                .param("reference", reference)
                .param("id", id)
                .update();
    }

    void submitData(UUID id, int version) {
        jdbc.sql("""
                        insert into engine.request_data (procedure, version, answers)
                        values (:id, :version, '{"destino": "Lisboa"}')""").param("id", id).param("version", version).update();
    }

    UUID createTask(UUID procedure) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into engine.task (id, procedure, step, handled_by)
                        values (:id, :procedure, 'validacao-chefia', 'chefias')""").param("id", id).param("procedure", procedure).update();
        return id;
    }

    void record(UUID procedure, String action) {
        jdbc.sql("""
                        insert into engine.audit_entry
                            (procedure, actor_issuer, actor_subject, actor_name, actor_job_title, action)
                        values (:procedure, :issuer, 'ana-ferreira', 'Ana Ferreira', 'Técnica Superior', :action)""")
                .param("procedure", procedure)
                .param("issuer", ISSUER)
                .param("action", action)
                .update();
    }
}
