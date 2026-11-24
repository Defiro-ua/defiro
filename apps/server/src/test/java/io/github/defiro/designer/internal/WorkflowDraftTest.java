package io.github.defiro.designer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.spec.FieldSpecification;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.Transition;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorkflowDraftTest {

    static final PluginRef SUBMISSION = new PluginRef("submission", "0.1.0");
    static final PluginRef REVIEW = new PluginRef("review", "0.1.0");
    static final PluginRef FINAL_VALIDATION = new PluginRef("final-validation", "0.1.0");
    static final Set<PluginRef> CATALOGUE = Set.of(SUBMISSION, REVIEW, FINAL_VALIDATION);
    /** Who can request and who can handle a step, as the Designer offers them (UC05) */
    static final Groups GROUPS = new Groups(
            List.of("Todos os trabalhadores", "Divisão Financeira"),
            List.of("Chefias da unidade do requerente", "Divisão Financeira"));

    static final List<String> DECISIONS = List.of("approved", "returned", "rejected");
    static final Map<PluginRef, List<String>> OUTCOMES =
            Map.of(SUBMISSION, List.of("submitted"), REVIEW, DECISIONS, FINAL_VALIDATION, DECISIONS);

    static final Problem NO_NAME = new Problem("name", "Dê um nome ao workflow.");
    static final String ONE_FINAL_VALIDATION_LAST =
            "A validação final fecha o procedimento: só pode haver uma e tem de ser o último passo.";

    @Test
    void aNewDraftStartsWithARequestEveryEmployeeCanMake() {
        WorkflowDraft draft = WorkflowDraft.named("Ajudas de custo", SUBMISSION);

        assertThat(draft.steps()).singleElement().satisfies(request -> {
            assertThat(request.plugin()).isEqualTo(SUBMISSION);
            assertThat(request.name()).isEqualTo("Pedido");
            assertThat(request.handledBy()).isEqualTo("Todos os trabalhadores");
        });
    }

    @Test
    void aCompleteWorkflowHasNoProblems() {
        WorkflowDraft draft = withSteps(complete("Validação da chefia"), finalValidation());

        assertThat(draft.problems(CATALOGUE, GROUPS)).isEmpty();
    }

    @Test
    void aWorkflowWithNoNameIsRefusedOnItsName() {
        assertThat(named("", complete("Validação da chefia")).problems(CATALOGUE, GROUPS))
                .containsExactly(NO_NAME);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   ", "\t"})
    void blankOrMissingNameIsNoName(String name) {
        assertThat(named(name, complete("Validação da chefia")).problems(CATALOGUE, GROUPS))
                .containsExactly(NO_NAME);
    }

    @Test
    void aReviewStepNeedsANameSomeoneToHandleItAndADeadline() {
        Step unanswered =
                new Step("n2", REVIEW, "", null, null, List.of(), List.of("Pedido não autorizado"), List.of());

        assertThat(withSteps(unanswered).problems(CATALOGUE, GROUPS))
                .containsExactly(
                        new Problem("steps[1].name", "Passo 2: falta o nome."),
                        new Problem("steps[1].handledBy", "Passo 2: escolha quem trata."),
                        new Problem("steps[1].deadline", "Passo 2: indique o prazo legal, de 1 a 90 dias úteis."));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 91, 200, -3})
    void aDeadlineOutsideOneToNinetyWorkingDaysIsAProblem(int days) {
        Step step = complete("Validação da chefia").withDeadline(days);

        assertThat(withSteps(step).problems(CATALOGUE, GROUPS))
                .containsExactly(new Problem(
                        "steps[1].deadline",
                        "Passo 2 («Validação da chefia»): o prazo legal tem de ser de 1 a 90 dias úteis."));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 90})
    void oneAndNinetyWorkingDaysAreDeadlinesToo(int days) {
        assertThat(withSteps(complete("Validação da chefia").withDeadline(days)).problems(CATALOGUE, GROUPS))
                .isEmpty();
    }

    @Test
    void aStepWithNoReasonAtAllIsAProblemOfThatStep() {
        WorkflowDraft draft = withSteps(
                complete("Validação da chefia"),
                complete("Verificação financeira").withReasons(List.of(), List.of()));

        assertThat(draft.problems(CATALOGUE, GROUPS))
                .containsExactly(
                        new Problem(
                                "steps[2].reasons",
                                "Passo 3 («Verificação financeira»): acrescente pelo menos um motivo para devolver ou rejeitar."));
    }

    @Test
    void aStepMayHaveOnlyRejectReasons() {
        Step step = complete("Validação da chefia").withReasons(List.of(), List.of("Pedido não autorizado"));

        assertThat(withSteps(step).problems(CATALOGUE, GROUPS)).isEmpty();
    }

    @Test
    void aReturnReasonWithNoNameIsAProblemOfThatReason() {
        Step step = complete("Validação da chefia").withReasons(List.of(""), List.of());

        assertThat(withSteps(step).problems(CATALOGUE, GROUPS))
                .containsExactly(new Problem(
                        "steps[1].returnReasons[0]",
                        "Passo 2 («Validação da chefia»): dê um nome ao motivo de devolução 1."));
    }

    @Test
    void rejectReasonsAreCountedInTheirOwnList() {
        Step step = complete("Validação da chefia")
                .withReasons(List.of("Preenchido incorretamente"), List.of("Pedido não autorizado", " "));

        assertThat(withSteps(step).problems(CATALOGUE, GROUPS))
                .containsExactly(new Problem(
                        "steps[1].rejectReasons[1]",
                        "Passo 2 («Validação da chefia»): dê um nome ao motivo de rejeição 2."));
    }

    @Test
    void theRequestNeedsWhoCanMakeIt() {
        WorkflowDraft draft = withSteps(complete("Validação da chefia"));
        Step request = draft.steps().getFirst().withHandledBy("");
        draft = new WorkflowDraft(draft.name(), List.of(request, draft.steps().get(1)));

        assertThat(draft.problems(CATALOGUE, GROUPS))
                .containsExactly(new Problem("steps[0].handledBy", "Passo 1 («Pedido»): escolha quem trata."));
    }

    @Test
    void aPathThatDoesNotStartWithTheRequestIsAProblem() {
        Step request =
                WorkflowDraft.named("Ajudas de custo", SUBMISSION).steps().getFirst();
        WorkflowDraft draft = new WorkflowDraft("Ajudas de custo", List.of(complete("Validação da chefia"), request));

        assertThat(draft.problems(CATALOGUE, GROUPS))
                .contains(new Problem("steps", "O caminho começa pelo pedido, e só tem um pedido."));
    }

    @Test
    void theFinalValidationMustBeLast() {
        WorkflowDraft draft = withSteps(finalValidation(), complete("Validação da chefia"));

        assertThat(draft.problems(CATALOGUE, GROUPS)).containsExactly(new Problem("steps", ONE_FINAL_VALIDATION_LAST));
    }

    @Test
    void thereIsOnlyOneFinalValidation() {
        WorkflowDraft draft = withSteps(complete("Validação da chefia"), finalValidation(), finalValidation());

        assertThat(draft.problems(CATALOGUE, GROUPS)).containsExactly(new Problem("steps", ONE_FINAL_VALIDATION_LAST));
    }

    @Test
    void aStepTypeMissingFromTheCatalogueIsAProblem() {
        Step step = complete("Validação da chefia").withPlugin(new PluginRef("review", "9.9.9"));

        assertThat(withSteps(step).problems(CATALOGUE, GROUPS))
                .containsExactly(new Problem(
                        "steps[1].plugin",
                        "Passo 2 («Validação da chefia»): o tipo de passo review@9.9.9 não está no catálogo."));
    }

    @Test
    void theSpecificationHasThePathInOrder() {
        WorkflowDraft draft = withSteps(complete("Validação da chefia"), finalValidation());

        WorkflowSpecification specification = draft.specification(OUTCOMES::get);

        assertThat(specification.schemaVersion()).isEqualTo(WorkflowSpecification.SCHEMA_VERSION);
        assertThat(specification.name()).isEqualTo("Ajudas de custo");
        assertThat(specification.steps())
                .extracting(each -> each.name() + " " + each.plugin())
                .containsExactly(
                        "Pedido submission@0.1.0",
                        "Validação da chefia review@0.1.0",
                        "Validação final final-validation@0.1.0");
        assertThat(specification.steps().get(1).returnReasons()).containsExactly("Preenchido incorretamente");
        assertThat(specification.steps().get(1).rejectReasons()).containsExactly("Pedido não autorizado");
    }

    @Test
    void approvingGoesOnReturningGoesBackToTheRequesterAndRejectingEnds() {
        WorkflowDraft draft = withSteps(complete("Validação da chefia"), finalValidation());
        String request = draft.steps().get(0).id();
        String review = draft.steps().get(1).id();
        String last = draft.steps().get(2).id();

        assertThat(draft.specification(OUTCOMES::get).transitions())
                .containsExactly(
                        new Transition(request, "submitted", review),
                        new Transition(review, "approved", last),
                        new Transition(review, "returned", request),
                        new Transition(review, "rejected", null),
                        new Transition(last, "approved", null),
                        new Transition(last, "returned", request),
                        new Transition(last, "rejected", null));
    }

    @Test
    void aWorkflowFileGivesBackTheDraftItWasMadeFrom() {
        WorkflowDraft draft = withSteps(complete("Validação da chefia"), finalValidation());

        assertThat(WorkflowDraft.from(draft.specification(OUTCOMES::get))).isEqualTo(draft);
    }

    @Test
    void theTransitionsOfAFileAreMadeAgainFromThePath() {
        WorkflowDraft draft = withSteps(complete("Validação da chefia"));
        WorkflowSpecification file = draft.specification(OUTCOMES::get);
        WorkflowSpecification edited = new WorkflowSpecification(
                file.schemaVersion(), file.name(), file.steps(), List.of(new Transition("x", "approved", "y")));

        assertThat(WorkflowDraft.from(edited).specification(OUTCOMES::get).transitions())
                .isEqualTo(file.transitions());
    }

    @Test
    void aWorkflowWithNoQuestionForTheRequesterIsRefused() {
        assertThat(asking().problems(CATALOGUE, GROUPS))
                .containsExactly(
                        new Problem(
                                "steps[0].fields",
                                "Formulário do pedido: ainda não tem perguntas para quem faz o pedido… crie pelo menos uma pergunta, por exemplo «Material pedido»"));
    }

    @Test
    void aQuestionNeedsALabel() {
        FieldSpecification unlabelled = new FieldSpecification("q2", " ", "number", false, List.of());

        assertThat(asking(MATERIAL, unlabelled).problems(CATALOGUE, GROUPS))
                .containsExactly(
                        new Problem("steps[0].fields[1].label", "Formulário do pedido, campo 2: falta o rótulo."));
    }

    @Test
    void aSingleChoiceNeedsAtLeastTwoOptions() {
        FieldSpecification transport =
                new FieldSpecification("q1", "Transporte", "single-choice", true, List.of("Carro próprio", " "));

        assertThat(asking(transport).problems(CATALOGUE, GROUPS))
                .containsExactly(new Problem(
                        "steps[0].fields[0].options",
                        "Formulário do pedido, campo 1 («Transporte»): faltam opções de resposta (pelo menos duas)."));
        assertThat(asking(new FieldSpecification(
                                "q1", "Transporte", "single-choice", true, List.of("Carro", "Comboio")))
                        .problems(CATALOGUE, GROUPS))
                .isEmpty();
    }

    @Test
    void aQuestionHasOneOfTheAnswerTypes() {
        FieldSpecification colour = new FieldSpecification("q1", "Cor", "colour", true, List.of());

        assertThat(asking(colour).problems(CATALOGUE, GROUPS))
                .containsExactly(new Problem(
                        "steps[0].fields[0].type",
                        "Formulário do pedido, campo 1 («Cor»): o tipo de resposta colour não existe."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short-text", "long-text", "number", "date", "date-time", "yes-no", "files"})
    void everyAnswerTypeButTheSingleChoiceNeedsNoOptions(String type) {
        assertThat(asking(new FieldSpecification("q1", "Pergunta", type, false, List.of()))
                        .problems(CATALOGUE, GROUPS))
                .isEmpty();
    }

    @Test
    void onlyTheRequestAsksQuestions() {
        Step review = complete("Validação da chefia").withFields(List.of(MATERIAL));

        assertThat(withSteps(review).problems(CATALOGUE, GROUPS))
                .containsExactly(
                        new Problem("steps[1].fields", "Passo 2 («Validação da chefia»): só o pedido tem perguntas."));
    }

    @Test
    void theQuestionsGoIntoTheWorkflowFileAndComeBack() {
        WorkflowDraft draft = asking(
                MATERIAL,
                new FieldSpecification("q2", "Transporte", "single-choice", true, List.of("Carro", "Comboio")));

        WorkflowSpecification file = draft.specification(OUTCOMES::get);

        assertThat(file.steps().getFirst().fields())
                .extracting(FieldSpecification::label)
                .containsExactly("Material pedido", "Transporte");
        assertThat(file.steps().get(1).fields()).isEmpty();
        assertThat(WorkflowDraft.from(file)).isEqualTo(draft);
    }

    @Test
    void theWorkflowFileKeepsOnlyTheOptionsWrittenAndOnlyForASingleChoice() {
        WorkflowDraft draft = asking(
                new FieldSpecification("q1", "Transporte", "single-choice", true, List.of("Carro", " ", "Comboio", "")),
                new FieldSpecification("q2", "Local", "short-text", true, List.of("left from a single choice")));

        List<FieldSpecification> questions =
                draft.specification(OUTCOMES::get).steps().getFirst().fields();

        assertThat(questions)
                .extracting(FieldSpecification::options)
                .containsExactly(List.of("Carro", "Comboio"), List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Departamento de Marketing", "Todos os trabalhadores"})
    void aStepIsHandledByOneOfTheGroupsTheDesignerOffers(String handledBy) {
        Step step = complete("Validação da chefia").withHandledBy(handledBy);

        assertThat(withSteps(step).problems(CATALOGUE, GROUPS))
                .containsExactly(new Problem(
                        "steps[1].handledBy",
                        "Passo 2 («Validação da chefia»): «" + handledBy
                                + "» não é um grupo nem uma unidade da organização."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Departamento de Marketing", "Chefias da unidade do requerente"})
    void theRequestIsMadeByOneOfTheGroupsTheDesignerOffers(String handledBy) {
        WorkflowDraft draft = withSteps(complete("Validação da chefia"));
        List<Step> path = new ArrayList<>(draft.steps());
        path.set(0, path.getFirst().withHandledBy(handledBy));

        assertThat(new WorkflowDraft(draft.name(), path).problems(CATALOGUE, GROUPS))
                .containsExactly(new Problem(
                        "steps[0].handledBy",
                        "Passo 1 («Pedido»): «" + handledBy + "» não é um grupo nem uma unidade da organização."));
    }

    @Test
    void aGroupTheDesignerOffersIsAccepted() {
        Step step = complete("Verificação financeira").withHandledBy("Divisão Financeira");

        assertThat(withSteps(step).problems(CATALOGUE, GROUPS)).isEmpty();
    }

    /** "Ajudas de custo": the request, then these steps. */
    static WorkflowDraft withSteps(Step... steps) {
        return named("Ajudas de custo", steps);
    }

    /** The workflow's request asks «Material pedido», so that only what a test is about is missing. */
    static WorkflowDraft named(String name, Step... steps) {
        Step request = WorkflowDraft.named(name, SUBMISSION).steps().getFirst().withFields(List.of(MATERIAL));
        List<Step> path = new ArrayList<>(List.of(request));
        path.addAll(List.of(steps));
        return new WorkflowDraft(name, path);
    }

    static final FieldSpecification MATERIAL =
            new FieldSpecification("material", "Material pedido", "short-text", true, List.of());

    static WorkflowDraft asking(FieldSpecification... fields) {
        WorkflowDraft draft = withSteps(complete("Validação da chefia"));
        List<Step> path = new ArrayList<>(draft.steps());
        path.set(0, path.getFirst().withFields(List.of(fields)));
        return new WorkflowDraft(draft.name(), path);
    }

    /** A review step with every answer: who handles it, a deadline and one reason of each kind. */
    static Step complete(String name) {
        return new Step(
                "id-" + name,
                REVIEW,
                name,
                "Chefias da unidade do requerente",
                5,
                List.of("Preenchido incorretamente"),
                List.of("Pedido não autorizado"),
                List.of());
    }

    static Step finalValidation() {
        return new Step(
                "id-final-" + System.nanoTime(),
                FINAL_VALIDATION,
                "Validação final",
                "Divisão Financeira",
                10,
                List.of("Preenchido incorretamente"),
                List.of("Pedido não autorizado"),
                List.of());
    }
}
