package io.github.ravismash.outputgate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * What one model call cost (R7). Each part may be unknown; {@link #UNKNOWN} has none.
 * Cost is in whatever unit you set your ceilings in (e.g. USD).
 *
 * @param cost cost of the call, or null when unknown
 * @param latency wall-clock time of the call, or null when unknown
 * @param outputTokens tokens the model produced, or null when unknown
 */
public record Usage(BigDecimal cost, Duration latency, Long outputTokens) {

    /** Nothing is known about the call. */
    public static final Usage UNKNOWN = new Usage(null, null, null);

    /** Rejects negative values. */
    public Usage {
        if (cost != null && cost.signum() < 0) {
            throw new IllegalArgumentException("cost must not be negative");
        }
        if (latency != null && latency.isNegative()) {
            throw new IllegalArgumentException("latency must not be negative");
        }
        if (outputTokens != null && outputTokens < 0) {
            throw new IllegalArgumentException("outputTokens must not be negative");
        }
    }

    /**
     * Usage with every part known.
     *
     * @param cost cost of the call
     * @param latency wall-clock time of the call
     * @param outputTokens tokens the model produced
     * @return the usage
     */
    public static Usage of(BigDecimal cost, Duration latency, long outputTokens) {
        return new Usage(cost, latency, outputTokens);
    }

    /** @return the cost, if known */
    public Optional<BigDecimal> knownCost() {
        return Optional.ofNullable(cost);
    }

    /** @return the latency, if known */
    public Optional<Duration> knownLatency() {
        return Optional.ofNullable(latency);
    }

    /** @return the output token count, if known */
    public OptionalLong knownOutputTokens() {
        return outputTokens == null ? OptionalLong.empty() : OptionalLong.of(outputTokens);
    }
}
