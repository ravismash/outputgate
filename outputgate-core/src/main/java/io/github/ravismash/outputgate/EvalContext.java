package io.github.ravismash.outputgate;

/**
 * Facts about a call that gates may need beyond the output itself.
 * Empty for now; usage (R7) and the source document (R6) are added with those gates.
 */
public final class EvalContext {

    private static final EvalContext EMPTY = new EvalContext();

    private EvalContext() {}

    /** A context with nothing in it. */
    public static EvalContext empty() {
        return EMPTY;
    }
}
