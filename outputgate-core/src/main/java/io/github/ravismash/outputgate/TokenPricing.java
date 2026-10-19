package io.github.ravismash.outputgate;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Turns token counts into cost (R11). Providers report tokens, not money, so adapters
 * need the price you pay. The unit is yours (e.g. USD per million tokens).
 *
 * @param inputPerMillion price per million prompt tokens
 * @param outputPerMillion price per million output tokens
 */
public record TokenPricing(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {

    /** A local model with no per-token price. */
    public static final TokenPricing FREE = new TokenPricing(BigDecimal.ZERO, BigDecimal.ZERO);

    /** Rejects missing or negative prices. */
    public TokenPricing {
        if (inputPerMillion == null || outputPerMillion == null
                || inputPerMillion.signum() < 0 || outputPerMillion.signum() < 0) {
            throw new IllegalArgumentException("token prices must be zero or more");
        }
    }

    /**
     * @param input price per million prompt tokens
     * @param output price per million output tokens
     * @return the pricing
     */
    public static TokenPricing perMillionTokens(BigDecimal input, BigDecimal output) {
        return new TokenPricing(input, output);
    }

    /**
     * The exact cost of a call.
     *
     * @param inputTokens prompt tokens, or null when unknown
     * @param outputTokens output tokens, or null when unknown
     * @return the cost, or empty when either count is unknown
     */
    public Optional<BigDecimal> cost(Long inputTokens, Long outputTokens) {
        if (inputTokens == null || outputTokens == null) {
            return Optional.empty();
        }
        BigDecimal total = inputPerMillion.multiply(BigDecimal.valueOf(inputTokens))
                .add(outputPerMillion.multiply(BigDecimal.valueOf(outputTokens)));
        return Optional.of(total.movePointLeft(6).stripTrailingZeros());
    }
}
