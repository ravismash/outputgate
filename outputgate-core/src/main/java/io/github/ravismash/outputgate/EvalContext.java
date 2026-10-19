package io.github.ravismash.outputgate;

import java.util.Optional;

/**
 * Facts about a call that gates may need beyond the output itself: the source document
 * for grounding (R6). Usage for BudgetGate (R7) arrives with {@code LlmOutput}.
 */
public final class EvalContext {

    private static final EvalContext EMPTY = new EvalContext(null);

    private final SourceDocument source;

    private EvalContext(SourceDocument source) {
        this.source = source;
    }

    /** A context with nothing in it. */
    public static EvalContext empty() {
        return EMPTY;
    }

    /**
     * A copy of this context that carries {@code source}.
     *
     * @param source the document the output was extracted from
     * @return the new context
     */
    public EvalContext withSource(SourceDocument source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        return new EvalContext(source);
    }

    /**
     * The source document, if one was given.
     *
     * @return the source document, or empty
     */
    public Optional<SourceDocument> source() {
        return Optional.ofNullable(source);
    }
}
