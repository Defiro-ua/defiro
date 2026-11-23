package io.github.defiro.plugin;

/** What a step does when a transition arrives at it: it waits for people, or it is done at once. */
public sealed interface Entry {

    /**
     * The step waits for people: the Engine gives a task to whoever handles it.
     *
     * @param handledBy the id of the group or unit that handles the step
     */
    record Wait(String handledBy) implements Entry {

        public Wait {
            if (handledBy == null || handledBy.isBlank()) {
                throw new IllegalArgumentException("A step that waits needs who handles it");
            }
        }
    }

    /**
     * The step finished without anyone acting, for example after generating a document.
     *
     * @param outcome how it ended, which picks the transition to the next step
     */
    record Done(String outcome) implements Entry {

        public Done {
            if (outcome == null || outcome.isBlank()) {
                throw new IllegalArgumentException("A step that is done needs its outcome");
            }
        }
    }
}
