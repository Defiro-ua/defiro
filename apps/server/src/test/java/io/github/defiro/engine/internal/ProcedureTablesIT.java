package io.github.defiro.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.defiro.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The rules the {@code engine} schema keeps by itself for procedures, their participants, the
 * versions of their data, their tasks and their history, whatever code writes to it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProcedureTablesIT {

    static final String SPECIFICATION = """
            {"schemaVersion": 1, "name": "Ajudas de custo", "steps": [], "transitions": []}""";
    static final String ANSWERS = """
            {"destino": "Lisboa", "partida": "2026-11-08T07:30"}""";
    static final String REQUESTER = """
            {"name": "Ana Ferreira", "jobTitle": "Técnica Superior", "unit": "Divisão de Obras Municipais", "manager": "Rui Matos"}""";

    @Autowired
    JdbcClient jdbc;

    final UUID procedure = UUID.randomUUID();
    final UUID ana = UUID.randomUUID();
    final UUID rui = UUID.randomUUID();
    final UUID carla = UUID.randomUUID();

    @Test
    void aProcedureKeepsItsOwnCopyOfTheSpecification() {
        startProcedure(procedure);

        assertThat(jdbc.sql("select specification ->> 'name' from engine.procedure where id = :id")
                        .param("id", procedure)
                        .query(String.class)
                        .single())
                .isEqualTo("Ajudas de custo");
    }

    @Test
    void aProcedureHasItsReferenceFromTheMomentItStarts() {
        assertThatThrownBy(() -> jdbc.sql("""
                                insert into engine.procedure
                                    (id, draft, version, specification, current_step, requester)
                                values (:id, :draft, 1, cast(:specification as jsonb), 'pedido', :requester)""")
                        .param("id", procedure)
                        .param("draft", UUID.randomUUID())
                        .param("specification", SPECIFICATION)
                        .param("requester", ana)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aProcedureStartsBeforeItIsSubmitted() {
        startProcedure(procedure);

        assertThat(jdbc.sql("select state from engine.procedure where id = :id")
                        .param("id", procedure)
                        .query(String.class)
                        .single())
                .isEqualTo("started");
    }

    @Test
    void twoProceduresCannotHaveTheSameReference() {
        String reference = referenceOf(procedure);
        startProcedure(procedure, reference);

        assertThatThrownBy(() -> startProcedure(UUID.randomUUID(), reference))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aProcedureIsInOneOfTheKnownStates() {
        startProcedure(procedure);

        assertThatThrownBy(() -> jdbc.sql("update engine.procedure set state = 'lost' where id = :id")
                        .param("id", procedure)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aProcedureThatEndedKeepsWhenItEnded() {
        startProcedure(procedure);

        assertThatThrownBy(() -> setState(procedure, "closed", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> setState(procedure, "rejected", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        setState(procedure, "closed", true);
    }

    @Test
    void aProcedureStillGoingHasNotEnded() {
        startProcedure(procedure);

        assertThatThrownBy(() -> setState(procedure, "in-progress", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anUndoneEndingOpensTheProcedureAgain() {
        startProcedure(procedure);
        setState(procedure, "rejected", true);

        setState(procedure, "in-progress", false);

        assertThat(jdbc.sql("select closed_at is null from engine.procedure where id = :id")
                        .param("id", procedure)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void aPersonTakesPartInAProcedureOnce() {
        startProcedure(procedure);
        addParticipant(procedure, rui);

        assertThatThrownBy(() -> addParticipant(procedure, rui)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theAnswersAreKeptByFieldId() {
        startProcedure(procedure);

        assertThatThrownBy(
                        () -> submitData(procedure, 1, "[{\"field\": \"destino\", \"value\": \"Lisboa\"}]", REQUESTER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSubmittedVersionKeepsTheRequesterAsTheyWere() {
        startProcedure(procedure);
        submitData(procedure, 1);

        assertThat(jdbc.sql("select requester ->> 'jobTitle' from engine.request_data where procedure = :id")
                        .param("id", procedure)
                        .query(String.class)
                        .single())
                .isEqualTo("Técnica Superior");
    }

    @Test
    void aSubmittedVersionNeedsTheRequester() {
        startProcedure(procedure);

        assertThatThrownBy(() -> submitData(procedure, 1, ANSWERS, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSubmittedVersionOfTheRequestCannotBeChanged() {
        startProcedure(procedure);
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
        startProcedure(procedure);
        submitData(procedure, 1);

        assertThatThrownBy(() -> jdbc.sql("delete from engine.request_data where procedure = :id")
                        .param("id", procedure)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("nunca muda");
    }

    @Test
    void eachResubmissionIsANewVersionOfTheRequest() {
        startProcedure(procedure);
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
    void anOpenTaskIsInTheInboxOfEachOfItsCandidates() {
        startProcedure(procedure);
        UUID task = createTask(procedure, List.of(rui, carla));

        assertThat(inbox(rui)).containsExactly(task);
        assertThat(inbox(carla)).containsExactly(task);
        assertThat(inbox(ana)).isEmpty();
    }

    @Test
    void theFirstCandidateToActCompletesTheTaskForEveryone() {
        startProcedure(procedure);
        UUID task = createTask(procedure, List.of(rui, carla));

        assertThat(complete(task, rui)).isEqualTo(1);
        assertThat(complete(task, carla)).isZero();
        assertThat(inbox(carla)).isEmpty();
    }

    @Test
    void aTaskCannotBeHalfCompleted() {
        startProcedure(procedure);
        UUID task = createTask(procedure, List.of(rui));

        assertThatThrownBy(() -> jdbc.sql("update engine.task set completed_at = now() where id = :id")
                        .param("id", task)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anEntryOfTheHistoryCannotBeChanged() {
        startProcedure(procedure);
        record(procedure, "requester", "submitted");

        assertThatThrownBy(() -> jdbc.sql("update engine.audit_entry set action = 'closed' where procedure = :id")
                        .param("id", procedure)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("nunca muda");
    }

    @Test
    void anEntryOfTheHistoryCannotBeDeleted() {
        startProcedure(procedure);
        record(procedure, "requester", "submitted");

        assertThatThrownBy(() -> jdbc.sql("delete from engine.audit_entry where procedure = :id")
                        .param("id", procedure)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("nunca muda");
    }

    @Test
    void theHistoryRecordsAFailedAction() {
        startProcedure(procedure);

        record(procedure, "reviewer", "failed");

        assertThat(jdbc.sql("select actor_role from engine.audit_entry where procedure = :id and action = 'failed'")
                        .param("id", procedure)
                        .query(String.class)
                        .single())
                .isEqualTo("reviewer");
    }

    @Test
    void theHistoryRecordsAnUndoneDecisionAndItsTask() {
        startProcedure(procedure);
        UUID task = createTask(procedure, List.of(rui));

        record(procedure, "reviewer", "undone", task);

        assertThat(jdbc.sql("select task from engine.audit_entry where procedure = :id and action = 'undone'")
                        .param("id", procedure)
                        .query(UUID.class)
                        .single())
                .isEqualTo(task);
    }

    @Test
    void anEntryCanOnlyBeAboutATaskThatExists() {
        startProcedure(procedure);

        assertThatThrownBy(() -> record(procedure, "reviewer", "approved", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theHistoryOnlyRecordsKnownActions() {
        startProcedure(procedure);

        assertThatThrownBy(() -> record(procedure, "requester", "deleted"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theHistoryOnlyRecordsKnownRoles() {
        startProcedure(procedure);

        assertThatThrownBy(() -> record(procedure, "director", "approved"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    void startProcedure(UUID id) {
        startProcedure(id, referenceOf(id));
    }

    void startProcedure(UUID id, String reference) {
        jdbc.sql("""
                        insert into engine.procedure
                            (id, draft, version, specification, reference, current_step, requester)
                        values (:id, :draft, 1, cast(:specification as jsonb), :reference, 'pedido', :requester)""")
                .param("id", id)
                .param("draft", UUID.randomUUID())
                .param("specification", SPECIFICATION)
                .param("reference", reference)
                .param("requester", ana)
                .update();
    }

    /** A reference no other test uses: the tests share one database */
    static String referenceOf(UUID procedure) {
        return "AC-2026-" + procedure;
    }

    void setState(UUID id, String state, boolean ended) {
        jdbc.sql(
                        "update engine.procedure set state = :state, closed_at = case when :ended then now() end where id = :id")
                .param("state", state)
                .param("ended", ended)
                .param("id", id)
                .update();
    }

    void addParticipant(UUID procedure, UUID person) {
        jdbc.sql("insert into engine.participant (procedure, person) values (:procedure, :person)")
                .param("procedure", procedure)
                .param("person", person)
                .update();
    }

    void submitData(UUID id, int version) {
        submitData(id, version, ANSWERS, REQUESTER);
    }

    void submitData(UUID id, int version, String answers, String requester) {
        jdbc.sql("""
                        insert into engine.request_data (procedure, version, answers, requester)
                        values (:id, :version, cast(:answers as jsonb), cast(:requester as jsonb))""")
                .param("id", id)
                .param("version", version)
                .param("answers", answers)
                .param("requester", requester)
                .update();
    }

    UUID createTask(UUID procedure, List<UUID> candidates) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into engine.task (id, procedure, step, assignee_rule)
                        values (:id, :procedure, 'validacao-chefia', '{"managersOfRequesterUnit": true}')""").param("id", id).param("procedure", procedure).update();
        for (UUID candidate : candidates) {
            jdbc.sql("insert into engine.task_candidate (task, person) values (:task, :person)")
                    .param("task", id)
                    .param("person", candidate)
                    .update();
        }
        return id;
    }

    List<UUID> inbox(UUID person) {
        return jdbc.sql("""
                        select t.id from engine.task t
                        join engine.task_candidate c on c.task = t.id
                        where c.person = :person and t.completed_at is null""").param("person", person).query(UUID.class).list();
    }

    int complete(UUID task, UUID person) {
        return jdbc.sql("""
                        update engine.task
                        set completed_at = now(), outcome = 'approved', completed_by = :person
                        where id = :id and completed_at is null""").param("person", person).param("id", task).update();
    }

    void record(UUID procedure, String role, String action) {
        record(procedure, role, action, null);
    }

    void record(UUID procedure, String role, String action, UUID task) {
        jdbc.sql("""
                        insert into engine.audit_entry (procedure, actor, actor_role, action, task)
                        values (:procedure, :actor, :role, :action, :task)""")
                .param("procedure", procedure)
                .param("actor", ana)
                .param("role", role)
                .param("action", action)
                .param("task", task)
                .update();
    }
}
