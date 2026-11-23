package io.github.defiro.plugin;

import java.util.List;
import java.util.Map;
import org.pf4j.ExtensionPoint;

/**
 * What a step of a workflow does. Each step names a plugin@version, and that plugin runs it.
 *
 * <p>The Engine calls a step in this order (the architecture's «Plugin step lifecycle»):
 *
 * <ol>
 *   <li>{@link #enter}: a transition arrives at the step. The plugin says whether the step waits for
 *       people, who then get a task, or is done at once, as document generation is.
 *   <li>{@link #check}: the data of the step, as often as needed, before anyone acts.
 *   <li>{@link #complete}: a person acts. The plugin gives the outcome, and the outcome picks the
 *       transition to the next step.
 * </ol>
 *
 * <p>The first step, the request, is never entered: the requester acts on it directly (check, then
 * complete), with no task.
 *
 * <p>If a plugin throws, the Engine fails the action and leaves the procedure as it was.
 */
public interface StepPlugin extends ExtensionPoint {

    /** A transition arrived at the step: it waits for people, or it is done at once. */
    Entry enter(StepContext context);

    /**
     * Checks the data of the step.
     *
     * @param data the answers, by field id
     * @return what is wrong, field by field; empty when the data is right
     */
    List<FieldProblem> check(StepContext context, Map<String, Object> data);

    /**
     * A person acted on the step.
     *
     * @return the outcome, for example "approved": one of the outcomes the Plugin Registry lists for
     *     this plugin
     */
    String complete(StepContext context, Decision decision);
}
