package io.github.ravismash.outputgate;

import java.util.Optional;

/**
 * Facts about a call that gates may need beyond the output itself: the source document
 * for grounding (R6) and the call's {@link Usage} (R7).
 */
public final class EvalContext {

    private static final EvalContext EMPTY = new EvalContext(null, Usage.UNKNOWN);

    private final SourceDocument source;
    private final Usage usage;

    private EvalContext(SourceDocument source, Usage usage) {
        this.source = source;
        this.usage = usage;
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
        return new EvalContext(source, usage);
    }

    /**
     * A copy of this context that carries {@code usage}.
     *
     * @param usage what the call cost; null means unknown
     * @return the new context
     */
    public EvalContext withUsage(Usage usage) {
        return new EvalContext(source, usage == null ? Usage.UNKNOWN : usage);
    }

    /**
     * The source document, if one was given.
     *
     * @return the source document, or empty
     */
    public Optional<SourceDocument> source() {
        return Optional.ofNullable(source);
    }

    /**
     * What the call cost; {@link Usage#UNKNOWN} unless set.
     *
     * @return the usage
     */
    public Usage usage() {
        return usage;
    }
}
