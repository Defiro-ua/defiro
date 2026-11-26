package io.github.defiro.plugin;

import java.util.List;

/** What a step does when a transition arrives at it: it waits for people, or it is done at once. */
public sealed interface Entry {

    /**
     * The step waits for people: the Engine gives a task to the people the rule describes.
     *
     * @param assignee who should act
     */
    record Wait(AssigneeRule assignee) implements Entry {

        public Wait {
            Require.present(assignee, "A step that waits needs who should act");
        }
    }

    /**
     * The step finished without anyone acting, for example after generating a document.
     *
     * @param outcome how it ended, which picks the transition to the next step
     * @param documents what it generated, which the Engine keeps with the procedure
     */
    record Done(String outcome, List<GeneratedDocument> documents) implements Entry {

        public Done {
            Require.text(outcome, "A step that is done needs its outcome");
            documents = Require.copy(documents);
        }
    }
}
