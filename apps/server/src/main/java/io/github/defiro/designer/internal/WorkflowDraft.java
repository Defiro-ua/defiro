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

    List<Problem> problems() {
        if (name == null || name.isBlank()) {
            return List.of(new Problem("name", "Dê um nome ao workflow."));
        }
        return List.of();
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
