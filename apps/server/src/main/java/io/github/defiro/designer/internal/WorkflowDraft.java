package io.github.defiro.designer.internal;

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
                UUID.randomUUID().toString(), submission, "Pedido", "Todos os trabalhadores", null, null, null);
        return new WorkflowDraft(name, List.of(request));
    }

    /** Every missing or invalid answer, in the order the Workflow Manager meets them. */
    List<Problem> problems(Set<PluginRef> catalogue) {
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
            problems.addAll(problemsOfStep(k, catalogue));
        }
        return problems;
    }

    /**
     * The specification of this path. Each outcome of a step's plugin leads somewhere: «returned»
     * back to the requester, to be corrected (UC20); «rejected» to the end; any other, such as
     * «approved», to the next step, or to the end after the last one.
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
                        step.rejectReasons()))
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

    private boolean startsWithTheOnlyRequest() {
        return !steps.isEmpty() && is(steps.getFirst(), SUBMISSION) && count(SUBMISSION) == 1;
    }

    private boolean endsWithTheOnlyFinalValidation() {
        long finals = count(FINAL_VALIDATION);
        return finals == 0 || (finals == 1 && is(steps.getLast(), FINAL_VALIDATION));
    }

    private List<Problem> problemsOfStep(int k, Set<PluginRef> catalogue) {
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
        if (isBlank(step.handledBy())) {
            problems.add(new Problem(field + ".handledBy", where + "escolha quem trata."));
        }
        if (!is(step, SUBMISSION)) {
            problems.addAll(problemsOfDecision(step, field, where));
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
        problems.addAll(unnamed(step.returnReasons(), field + ".returnReasons", where));
        problems.addAll(unnamed(step.rejectReasons(), field + ".rejectReasons", where));
        return problems;
    }

    private static List<Problem> unnamed(List<String> reasons, String field, String where) {
        List<Problem> problems = new ArrayList<>();
        for (int j = 0; j < reasons.size(); j++) {
            if (isBlank(reasons.get(j))) {
                problems.add(new Problem(field + "[" + j + "]", where + "dê um nome ao motivo " + (j + 1) + "."));
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
