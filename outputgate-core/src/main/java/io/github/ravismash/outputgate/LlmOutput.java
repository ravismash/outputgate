package io.github.ravismash.outputgate;

/**
 * What a model returned: its raw text and the {@link Usage} of the call. Passed to
 * {@link Contract#evaluate(LlmOutput)} so gates such as {@link BudgetGate} can see the usage.
 *
 * @param text the raw text, exactly as the model returned it
 * @param usage what the call cost; {@link Usage#UNKNOWN} when not measured
 */
public record LlmOutput(String text, Usage usage) {

    /** Treats a null usage as unknown. */
    public LlmOutput {
        usage = usage == null ? Usage.UNKNOWN : usage;
    }

    /**
     * Raw text with unknown usage.
     *
     * @param text the raw text
     * @return the output
     */
    public static LlmOutput of(String text) {
        return new LlmOutput(text, Usage.UNKNOWN);
    }
}
