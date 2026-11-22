package io.github.defiro.engine.internal;

import io.github.defiro.identity.Person;
import io.github.defiro.spec.StepSpecification;
import io.github.defiro.spec.WorkflowSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * TEMPORARY, for UC27 (Daniel): the least of the task inbox that UC17 needs, the open tasks a
 * person is a candidate for. UC27 builds the real one, with deadlines, filters and the requests to
 * correct.
 */
@Component
class Inbox {

    private final Tasks tasks;
    private final SpecificationVersions versions;
    private final Candidates candidates;

    Inbox(Tasks tasks, SpecificationVersions versions, Candidates candidates) {
        this.tasks = tasks;
        this.versions = versions;
        this.candidates = candidates;
    }

    record Item(UUID task, String reference, String requester, String step) {}

    List<Item> of(Person person) {
        Map<String, WorkflowSpecification> specifications = new HashMap<>();
        List<Item> items = new ArrayList<>();
        for (Tasks.OpenTask task : tasks.open()) {
            WorkflowSpecification specification = specifications.computeIfAbsent(
                    task.draft() + "/" + task.version(),
                    key -> versions.find(task.draft(), task.version()).orElseThrow());
            StepSpecification step = step(specification, task.step());
            if (candidates.canHandle(step.handledBy(), task.requesterUnit(), person)) {
                items.add(new Item(task.id(), task.reference(), task.requesterName(), step.name()));
            }
        }
        return items;
    }

    static StepSpecification step(WorkflowSpecification specification, String id) {
        return specification.steps().stream()
                .filter(step -> step.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No step " + id + " in " + specification.name()));
    }
}
