package io.github.defiro.designer.internal;

import io.github.defiro.spec.FieldSpecification;
import io.github.defiro.spec.PluginRef;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.Transition;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * A workflow as the Workflow Manager is designing it. The browser changes it and the Designer keeps
 * it; when publishing, the Designer checks it against every rule and turns it into the
 * specification the Engine runs.
 */
record WorkflowDraft(String name, List<Step> steps) {

    static final String SUBMISSION = "submission";
    static final String FINAL_VALIDATION = "final-validation";

    private static final int SHORTEST_DEADLINE = 1;
    private static final int LONGEST_DEADLINE = 90;

    WorkflowDraft {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** A new draft: its path holds the request, which every employee can make. */
    static WorkflowDraft named(String name, PluginRef submission) {
        Step request = new Step(
                UUID.randomUUID().toString(), submission, "Pedido", "Todos os trabalhadores", null, null, null, null);
        return new WorkflowDraft(name, List.of(request));
    }

    /**
     * The draft a workflow file describes: its name and its path, step by step, as it was. The
     * transitions are not read: publishing makes them again from the order of the path, so a file
     * cannot send a request anywhere the path does not.
     */
    static WorkflowDraft from(WorkflowSpecification file) {
        List<Step> path = file.steps().stream()
                .map(step -> new Step(
                        step.id(),
                        step.plugin(),
                        step.name(),
                        step.handledBy(),
                        step.deadline(),
                        step.returnReasons(),
                        step.rejectReasons(),
                        step.fields()))
                .toList();
        return new WorkflowDraft(file.name(), path);
    }

    /**
     * Every missing or invalid answer, in the order the Workflow Manager meets them.
     *
     * @param groups who can make the request and who can handle a step (UC05)
     */
    List<Problem> problems(Set<PluginRef> catalogue, Groups groups) {
        List<Problem> problems = new ArrayList<>();
        if (isBlank(name)) {
            problems.add(new Problem("name", "Dê um nome ao workflow."));
        }
        if (!startsWithTheOnlyRequest()) {
            problems.add(new Problem("steps", "O caminho começa pelo pedido, e só tem um pedido."));
        }
        if (!endsWithTheOnlyFinalValidation()) {
            problems.add(new Problem(
                    "steps", "A validação final fecha o procedimento: só pode haver uma e tem de ser o último passo."));
        }
        for (int k = 0; k < steps.size(); k++) {
            problems.addAll(problemsOfStep(k, catalogue, groups));
        }
        if (!steps.isEmpty()) {
            problems.addAll(problemsOfForm(steps.getFirst().fields()));
        }
        return problems;
    }

    /**
     * The specification of this path. The Workflow Manager decides where an approved request goes
     * by choosing the steps and their order: «approved» (or any other outcome that moves the request
     * on) leads to the next step, or to the end after the last one. In the MVP, «returned» always
     * goes back to the requester, to be corrected (UC18, UC20), and «rejected» ends the procedure.
     */
    WorkflowSpecification specification(Function<PluginRef, List<String>> outcomes) {
        List<StepSpecification> path = steps.stream()
                .map(step -> new StepSpecification(
                        step.id(),
                        step.plugin(),
                        step.name(),
                        step.handledBy(),
                        step.deadline(),
                        step.returnReasons(),
                        step.rejectReasons(),
                        published(step.fields())))
                .toList();
        List<Transition> transitions = new ArrayList<>();
        String request = steps.getFirst().id();
        for (int k = 0; k < steps.size(); k++) {
            String from = steps.get(k).id();
            String next = k + 1 < steps.size() ? steps.get(k + 1).id() : null;
            for (String outcome : outcomes.apply(steps.get(k).plugin())) {
                String to = switch (outcome) {
                    case "returned" -> request;
                    case "rejected" -> null;
                    default -> next;
                };
                transitions.add(new Transition(from, outcome, to));
            }
        }
        return new WorkflowSpecification(WorkflowSpecification.SCHEMA_VERSION, name, path, transitions);
    }

    /**
     * The questions as the request form shows them: a single choice offers the options written, and
     * no other answer type has options, whatever the draft still keeps from an earlier type.
     */
    private static List<FieldSpecification> published(List<FieldSpecification> fields) {
        return fields.stream()
                .map(question -> new FieldSpecification(
                        question.id(),
                        question.label(),
                        question.type(),
                        question.required(),
                        FieldSpecification.SINGLE_CHOICE.equals(question.type())
                                ? question.options().stream()
                                        .filter(option -> !isBlank(option))
                                        .map(String::trim)
                                        .toList()
                                : List.of()))
                .toList();
    }

    private boolean startsWithTheOnlyRequest() {
        return !steps.isEmpty() && is(steps.getFirst(), SUBMISSION) && count(SUBMISSION) == 1;
    }

    private boolean endsWithTheOnlyFinalValidation() {
        long finals = count(FINAL_VALIDATION);
        return finals == 0 || (finals == 1 && is(steps.getLast(), FINAL_VALIDATION));
    }

    private List<Problem> problemsOfStep(int k, Set<PluginRef> catalogue, Groups groups) {
        Step step = steps.get(k);
        String field = "steps[" + k + "]";
        String where = "Passo " + (k + 1) + (isBlank(step.name()) ? "" : " («" + step.name() + "»)") + ": ";
        List<Problem> problems = new ArrayList<>();
        if (!catalogue.contains(step.plugin())) {
            problems.add(new Problem(
                    field + ".plugin", where + "o tipo de passo " + step.plugin() + " não está no catálogo."));
        }
        if (isBlank(step.name())) {
            problems.add(new Problem(field + ".name", where + "falta o nome."));
        }
        List<String> offered = is(step, SUBMISSION) ? groups.requesters() : groups.handlers();
        if (isBlank(step.handledBy())) {
            problems.add(new Problem(field + ".handledBy", where + "escolha quem trata."));
        } else if (!offered.contains(step.handledBy())) {
            problems.add(new Problem(
                    field + ".handledBy",
                    where + "«" + step.handledBy() + "» não é um grupo nem uma unidade da organização."));
        }
        if (!is(step, SUBMISSION)) {
            problems.addAll(problemsOfDecision(step, field, where));
        }
        if (k > 0 && !step.fields().isEmpty()) {
            problems.add(new Problem(field + ".fields", where + "só o pedido tem perguntas."));
        }
        return problems;
    }

    /** A step that decides on the request needs a legal deadline (UC06) and its reasons. */
    private static List<Problem> problemsOfDecision(Step step, String field, String where) {
        List<Problem> problems = new ArrayList<>();
        if (step.deadline() == null) {
            problems.add(new Problem(field + ".deadline", where + "indique o prazo legal, de 1 a 90 dias úteis."));
        } else if (step.deadline() < SHORTEST_DEADLINE || step.deadline() > LONGEST_DEADLINE) {
            problems.add(new Problem(field + ".deadline", where + "o prazo legal tem de ser de 1 a 90 dias úteis."));
        }
        if (step.returnReasons().isEmpty() && step.rejectReasons().isEmpty()) {
            problems.add(new Problem(
                    field + ".reasons", where + "acrescente pelo menos um motivo para devolver ou rejeitar."));
        }
        problems.addAll(
                unnamed(step.returnReasons(), field + ".returnReasons", where + "dê um nome ao motivo de devolução "));
        problems.addAll(
                unnamed(step.rejectReasons(), field + ".rejectReasons", where + "dê um nome ao motivo de rejeição "));
        return problems;
    }

    /**
     * The request form asks at least one question (UC04, criterion 7), and each question has a
     * label, an answer type that exists and, for a single choice, at least two options.
     */
    private static List<Problem> problemsOfForm(List<FieldSpecification> fields) {
        String form = "Formulário do pedido";
        if (fields.isEmpty()) {
            return List.of(
                    new Problem(
                            "steps[0].fields",
                            form
                                    + ": ainda não tem perguntas para quem faz o pedido… crie pelo menos uma pergunta, por exemplo «Material pedido»"));
        }
        List<Problem> problems = new ArrayList<>();
        for (int j = 0; j < fields.size(); j++) {
            FieldSpecification question = fields.get(j);
            String at = "steps[0].fields[" + j + "]";
            String where = form + ", campo " + (j + 1)
                    + (isBlank(question.label()) ? "" : " («" + question.label() + "»)") + ": ";
            if (isBlank(question.label())) {
                problems.add(new Problem(at + ".label", where + "falta o rótulo."));
            }
            if (!FieldSpecification.TYPES.contains(question.type())) {
                problems.add(
                        new Problem(at + ".type", where + "o tipo de resposta " + question.type() + " não existe."));
            } else if (FieldSpecification.SINGLE_CHOICE.equals(question.type())
                    && question.options().stream()
                                    .filter(option -> !isBlank(option))
                                    .count()
                            < 2) {
                problems.add(new Problem(at + ".options", where + "faltam opções de resposta (pelo menos duas)."));
            }
        }
        return problems;
    }

    /** Reasons are numbered in their own list, so the message says which list. */
    private static List<Problem> unnamed(List<String> reasons, String field, String message) {
        List<Problem> problems = new ArrayList<>();
        for (int j = 0; j < reasons.size(); j++) {
            if (isBlank(reasons.get(j))) {
                problems.add(new Problem(field + "[" + j + "]", message + (j + 1) + "."));
            }
        }
        return problems;
    }

    private long count(String plugin) {
        return steps.stream().filter(step -> is(step, plugin)).count();
    }

    private static boolean is(Step step, String plugin) {
        return step.plugin() != null && plugin.equals(step.plugin().name());
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
