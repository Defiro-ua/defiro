package io.github.defiro.designer.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A workflow as the Workflow Manager is designing it, before it is created. Every change gives a
 * new draft, so a draft never changes under whoever holds it.
 */
record WorkflowDraft(String name, List<Step> steps) {

    WorkflowDraft {
        steps = List.copyOf(steps);
    }

    /** A new draft: its path holds only the request step. */
    static WorkflowDraft named(String name) {
        return new WorkflowDraft(name, List.of(Step.request()));
    }

    WorkflowDraft withName(String newName) {
        return new WorkflowDraft(newName, steps);
    }

    /** Adds a step at the end of the path, but before the final validation, which stays last. */
    WorkflowDraft with(Step step) {
        boolean closed = steps.getLast().type() == StepType.FINAL_VALIDATION;
        if (closed && step.type() == StepType.FINAL_VALIDATION) {
            return this;
        }
        List<Step> path = new ArrayList<>(steps);
        path.add(closed ? path.size() - 1 : path.size(), step);
        return new WorkflowDraft(name, path);
    }

    /** Swaps a review step with its neighbour, if that neighbour is a review step too. */
    WorkflowDraft move(String stepId, Direction direction) {
        int from = indexOf(stepId);
        int to = direction == Direction.UP ? from - 1 : from + 1;
        if (from < 0 || to < 0 || to >= steps.size() || !isReview(from) || !isReview(to)) {
            return this;
        }
        List<Step> path = new ArrayList<>(steps);
        Collections.swap(path, from, to);
        return new WorkflowDraft(name, path);
    }

    /** Removes a step. «Pedido» stays: a workflow always starts with a request. */
    WorkflowDraft without(String stepId) {
        List<Step> path = new ArrayList<>(steps);
        path.removeIf(step -> step.id().equals(stepId) && step.type() != StepType.REQUEST);
        return new WorkflowDraft(name, path);
    }

    WorkflowDraft withReasons(String stepId, List<Reason> reasons) {
        List<Step> path = steps.stream()
                .map(step -> step.id().equals(stepId) ? step.withReasons(reasons) : step)
                .toList();
        return new WorkflowDraft(name, path);
    }

    /** Every missing or invalid answer, in the order the Workflow Manager meets them. */
    List<Problem> problems() {
        List<Problem> problems = new ArrayList<>();
        if (isBlank(name)) {
            problems.add(new Problem("name", "Dê um nome ao workflow."));
        }
        for (int k = 0; k < steps.size(); k++) {
            if (steps.get(k).type() != StepType.REQUEST) {
                problems.addAll(problemsOfStep(k));
            }
        }
        return problems;
    }

    private List<Problem> problemsOfStep(int k) {
        Step step = steps.get(k);
        String field = "steps[" + k + "]";
        String where = "Passo " + (k + 1) + (isBlank(step.name()) ? "" : " («" + step.name() + "»)") + ": ";
        List<Problem> problems = new ArrayList<>();
        if (isBlank(step.name())) {
            problems.add(new Problem(field + ".name", where + "falta o nome."));
        }
        if (isBlank(step.handledBy())) {
            problems.add(new Problem(field + ".handledBy", where + "escolha quem trata."));
        }
        if (step.deadline() == null) {
            problems.add(new Problem(field + ".deadline", where + "indique o prazo legal, em dias úteis."));
        }
        if (step.reasons().isEmpty()) {
            problems.add(new Problem(
                    field + ".reasons", where + "acrescente pelo menos um motivo para devolver ou rejeitar."));
        }
        for (int j = 0; j < step.reasons().size(); j++) {
            if (isBlank(step.reasons().get(j).name())) {
                problems.add(new Problem(
                        field + ".reasons[" + j + "].name", where + "dê um nome ao motivo " + (j + 1) + "."));
            }
        }
        return problems;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private int indexOf(String stepId) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).id().equals(stepId)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isReview(int index) {
        return steps.get(index).type() == StepType.REVIEW;
    }
}
