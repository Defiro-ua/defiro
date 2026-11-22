package io.github.defiro.engine.internal;

import java.util.List;
import java.util.UUID;

/**
 * What the audit log keeps when the requester corrects and resubmits a request, in the details of
 * its «resubmitted» entry: the version the request became, the answers this version replaced, and
 * the files it added. The document keeps only the current answers, so this is how a reviewer sees
 * what changed (UC17).
 *
 * <p>For UC20 (Dinis), who builds the correction: write this entry when the request is resubmitted.
 * Until then only the acceptance tests resubmit (ProcedureSeed).
 */
public record Resubmission(int version, List<RequestData.Answer> previousAnswers, List<UUID> newFiles) {

    public static final String ACTION = "resubmitted";

    public Resubmission {
        previousAnswers = List.copyOf(previousAnswers);
        newFiles = List.copyOf(newFiles);
    }
}
