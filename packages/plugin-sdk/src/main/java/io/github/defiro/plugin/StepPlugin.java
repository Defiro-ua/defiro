package io.github.defiro.plugin;

import java.util.List;
import java.util.Map;
import org.pf4j.ExtensionPoint;

/**
 * What a step of a workflow does: the plugin contract of the architecture (§7). Each step names a
 * plugin@version, and that plugin runs it. Built-in plugins use this contract like any other.
 *
 * <p>The Engine calls a step in this order (the architecture's «Plugin step lifecycle»):
 *
 * <ol>
 *   <li>{@link #enter}: a transition arrives at the step. The plugin says whether the step waits for
 *       people, who then get a task, or is done at once, as document generation is.
 *   <li>{@link #open} and {@link #check}: what the step shows, and whether its data is right, as
 *       often as needed before anyone acts.
 *   <li>{@link #complete}: a person acts. The plugin gives the outcome, and the outcome picks the
 *       transition to the next step.
 * </ol>
 *
 * <p>The first step, the request, is the exception: no transition arrives there, so it is not
 * entered, and the requester acts on it directly (open, check, complete), with no task. It is
 * entered only when a «returned» transition leads back to it.
 *
 * <p>Plugins keep no state of their own: everything they need comes in the {@link StepContext}.
 * They never read or write a store and never call the Engine. If a plugin throws, the Engine fails
 * the action, leaves the procedure as it was and records the failure in its history.
 */
public interface StepPlugin extends ExtensionPoint {

    /** What the plugin is, how a step of it is configured and how it can end. */
    Descriptor descriptor();

    /** A transition arrived at the step: it waits for people, or it is done at once. */
    Entry enter(StepContext context);

    /** What the step shows the person acting on it: its fields, its checks and the notes on it. */
    StepView open(StepContext context);

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
     * @return the outcome: one of the outcomes in this plugin's {@link #descriptor()}
     */
    String complete(StepContext context, Decision decision);
}
