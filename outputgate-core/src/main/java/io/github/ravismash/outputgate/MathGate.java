package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-field arithmetic checks (R5). A missing or non-numeric value is a violation,
 * never a pass.
 */
public final class MathGate implements Gate {

    private static final String NAME = "math";

    private final List<SumEquals> rules;
    private final BigDecimal tolerance;

    private MathGate(List<SumEquals> rules, BigDecimal tolerance) {
        this.rules = List.copyOf(rules);
        this.tolerance = tolerance;
    }

    /** Starts a new math gate. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Violation> check(JsonNode output, EvalContext context) {
        List<Violation> violations = new ArrayList<>();
        for (SumEquals rule : rules) {
            rule.check(output, tolerance, violations);
        }
        return violations;
    }

    private record SumEquals(String itemsPath, String totalPath) {

        void check(JsonNode output, BigDecimal tolerance, List<Violation> out) {
            List<Violation> found = new ArrayList<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (Paths.Hit item : Paths.resolve(output, itemsPath)) {
                BigDecimal n = number(item, found);
                if (n != null) {
                    sum = sum.add(n);
                }
            }
            List<Paths.Hit> totals = Paths.resolve(output, totalPath);
            BigDecimal total = null;
            if (totals.size() != 1) {
                found.add(new Violation(NAME, totalPath, "expected one total, found " + totals.size()));
            } else {
                total = number(totals.get(0), found);
            }
            if (!found.isEmpty()) {
                out.addAll(found);
                return;
            }
            if (sum.subtract(total).abs().compareTo(tolerance) > 0) {
                out.add(new Violation(NAME, totalPath, "sum of " + itemsPath + " is " + sum.toPlainString()
                        + " but " + totalPath + " is " + total.toPlainString()
                        + " (tolerance " + tolerance.toPlainString() + ")"));
            }
        }

        private static BigDecimal number(Paths.Hit hit, List<Violation> found) {
            JsonNode node = hit.node();
            if (node.isMissingNode() || node.isNull()) {
                found.add(new Violation(NAME, hit.path(), "no value"));
                return null;
            }
            if (!node.isNumber()) {
                found.add(new Violation(NAME, hit.path(), "not a number: " + node));
                return null;
            }
            return node.decimalValue();
        }
    }

    /** Builds a {@link MathGate}. */
    public static final class Builder {

        private final List<SumEquals> rules = new ArrayList<>();
        private BigDecimal tolerance = BigDecimal.ZERO;

        private Builder() {}

        /** Requires the numbers at {@code itemsPath} to add up to the number at {@code totalPath}. */
        public Builder sumEquals(String itemsPath, String totalPath) {
            rules.add(new SumEquals(Paths.validate(itemsPath), Paths.validate(totalPath)));
            return this;
        }

        /** Largest absolute difference still counted as equal. */
        public Builder tolerance(double tolerance) {
            return tolerance(BigDecimal.valueOf(tolerance));
        }

        /** Largest absolute difference still counted as equal. */
        public Builder tolerance(BigDecimal tolerance) {
            if (tolerance == null || tolerance.signum() < 0) {
                throw new IllegalArgumentException("tolerance must be zero or more");
            }
            this.tolerance = tolerance;
            return this;
        }

        /** Builds the gate; a math gate with no rule would pass everything, so that is an error. */
        public MathGate build() {
            if (rules.isEmpty()) {
                throw new IllegalStateException("MathGate needs at least one rule");
            }
            return new MathGate(rules, tolerance);
        }
    }
}
