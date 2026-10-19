package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Cost, latency and output-token ceilings on the call that produced the output (R7).
 * Usage comes from {@link LlmOutput} via {@link EvalContext#usage()}. A ceiling is
 * inclusive. Unknown usage with a ceiling set is a violation, never a pass.
 */
public final class BudgetGate implements Gate {

    private static final String NAME = "budget";

    private final BigDecimal maxCost;
    private final Duration maxLatency;
    private final Long maxOutputTokens;

    private BudgetGate(BigDecimal maxCost, Duration maxLatency, Long maxOutputTokens) {
        this.maxCost = maxCost;
        this.maxLatency = maxLatency;
        this.maxOutputTokens = maxOutputTokens;
    }

    /** Starts a new budget gate. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Violation> check(JsonNode output, EvalContext context) {
        Usage usage = context.usage();
        List<Violation> out = new ArrayList<>();
        if (maxCost != null) {
            var cost = usage.knownCost();
            if (cost.isEmpty()) {
                out.add(unknown("cost", maxCost.toPlainString()));
            } else if (cost.get().compareTo(maxCost) > 0) {
                out.add(over("cost", cost.get().toPlainString(), maxCost.toPlainString()));
            }
        }
        if (maxLatency != null) {
            var latency = usage.knownLatency();
            if (latency.isEmpty()) {
                out.add(unknown("latency", maxLatency.toMillis() + " ms"));
            } else if (latency.get().compareTo(maxLatency) > 0) {
                out.add(over("latency", latency.get().toMillis() + " ms", maxLatency.toMillis() + " ms"));
            }
        }
        if (maxOutputTokens != null) {
            OptionalLong tokens = usage.knownOutputTokens();
            if (tokens.isEmpty()) {
                out.add(unknown("output tokens", String.valueOf(maxOutputTokens)));
            } else if (tokens.getAsLong() > maxOutputTokens) {
                out.add(over("output tokens", String.valueOf(tokens.getAsLong()), String.valueOf(maxOutputTokens)));
            }
        }
        return out;
    }

    private static Violation unknown(String what, String ceiling) {
        return new Violation(NAME, "", what + " unknown, but a ceiling of " + ceiling + " is set");
    }

    private static Violation over(String what, String actual, String ceiling) {
        return new Violation(NAME, "", what + " " + actual + " is over the ceiling of " + ceiling);
    }

    /** Builds a {@link BudgetGate}. */
    public static final class Builder {

        private BigDecimal maxCost;
        private Duration maxLatency;
        private Long maxOutputTokens;

        private Builder() {}

        /**
         * The highest cost allowed, in the unit your {@link Usage} reports (e.g. USD).
         *
         * @param max the ceiling, zero or more
         * @return this builder
         */
        public Builder maxCost(BigDecimal max) {
            if (max == null || max.signum() < 0) {
                throw new IllegalArgumentException("cost ceiling must be zero or more");
            }
            this.maxCost = max;
            return this;
        }

        /**
         * The highest cost allowed.
         *
         * @param max the ceiling, zero or more
         * @return this builder
         */
        public Builder maxCost(double max) {
            return maxCost(BigDecimal.valueOf(max));
        }

        /**
         * The longest latency allowed.
         *
         * @param max the ceiling, zero or more
         * @return this builder
         */
        public Builder maxLatency(Duration max) {
            if (max == null || max.isNegative()) {
                throw new IllegalArgumentException("latency ceiling must be zero or more");
            }
            this.maxLatency = max;
            return this;
        }

        /**
         * The most output tokens allowed.
         *
         * @param max the ceiling, zero or more
         * @return this builder
         */
        public Builder maxOutputTokens(long max) {
            if (max < 0) {
                throw new IllegalArgumentException("output-token ceiling must be zero or more");
            }
            this.maxOutputTokens = max;
            return this;
        }

        /**
         * Builds the gate; a budget gate with no ceiling would pass everything, so that is an error.
         *
         * @return the gate
         */
        public BudgetGate build() {
            if (maxCost == null && maxLatency == null && maxOutputTokens == null) {
                throw new IllegalStateException("BudgetGate needs at least one ceiling");
            }
            return new BudgetGate(maxCost, maxLatency, maxOutputTokens);
        }
    }
}
