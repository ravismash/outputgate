package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cross-field arithmetic checks (R5): {@code sumEquals}, {@code sumEqualsRounded}, {@code equalsSum},
 * {@code productEquals}, {@code range} and custom rules, with one absolute tolerance.
 * A missing or non-numeric value is a violation, never a pass.
 */
public final class MathGate implements Gate {

    private static final String NAME = "math";

    private final List<Rule> rules;
    private final BigDecimal tolerance;

    private MathGate(List<Rule> rules, BigDecimal tolerance) {
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
        for (Rule rule : rules) {
            rule.check(output, tolerance, violations);
        }
        return violations;
    }

    private interface Rule {
        void check(JsonNode output, BigDecimal tolerance, List<Violation> out);
    }

    /** A number read from the output, with the concrete path it came from. */
    private record Num(String path, BigDecimal value) {}

    /** Reads every value at {@code path}; bad values become violations in {@code found}. */
    private static List<Num> read(JsonNode output, String path, List<Violation> found) {
        List<Num> nums = new ArrayList<>();
        for (Paths.Hit hit : Paths.resolve(output, path)) {
            JsonNode node = hit.node();
            if (node.isMissingNode() || node.isNull()) {
                found.add(new Violation(NAME, hit.path(), "no value"));
            } else if (!node.isNumber()) {
                found.add(new Violation(NAME, hit.path(), "not a number: " + node));
            } else {
                nums.add(new Num(hit.path(), node.decimalValue()));
            }
        }
        return nums;
    }

    private static boolean differs(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
        return a.subtract(b).abs().compareTo(tolerance) > 0;
    }

    private static String plain(BigDecimal n) {
        return n.toPlainString();
    }

    /** Items add up to a total; with {@code rounded}, also allowing half a unit of the last printed digit per figure. */
    private record SumEquals(String itemsPath, String totalPath, boolean rounded) implements Rule {

        @Override
        public void check(JsonNode output, BigDecimal tolerance, List<Violation> out) {
            List<Violation> found = new ArrayList<>();
            List<Num> items = read(output, itemsPath, found);
            List<Num> totals = read(output, totalPath, found);
            if (found.isEmpty() && totals.size() != 1) {
                found.add(new Violation(NAME, totalPath, "expected one total, found " + totals.size()));
            }
            if (!found.isEmpty()) {
                out.addAll(found);
                return;
            }
            BigDecimal sum = items.stream().map(Num::value).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal total = totals.get(0).value();
            BigDecimal allowed = tolerance;
            if (rounded) {
                int scale = total.scale();
                for (Num item : items) {
                    scale = Math.max(scale, item.value().scale());
                }
                BigDecimal halfUnit = BigDecimal.ONE.movePointLeft(Math.max(scale, 0)).divide(BigDecimal.TWO);
                allowed = allowed.max(halfUnit.multiply(BigDecimal.valueOf(items.size() + 1L)));
            }
            if (differs(sum, total, allowed)) {
                out.add(new Violation(NAME, totalPath, "sum of " + itemsPath + " is " + plain(sum)
                        + " but " + totalPath + " is " + plain(total)
                        + " (" + (rounded ? "allowed for rounding " : "tolerance ") + plain(allowed) + ")"));
            }
        }
    }

    /** {@code target = combine(operands)}, matched element by element. Used by equalsSum and productEquals. */
    private record ElementWise(String targetPath, List<String> operandPaths, boolean product) implements Rule {

        @Override
        public void check(JsonNode output, BigDecimal tolerance, List<Violation> out) {
            List<Violation> found = new ArrayList<>();
            List<Num> targets = read(output, targetPath, found);
            List<List<Num>> operands = new ArrayList<>();
            for (String path : operandPaths) {
                operands.add(read(output, path, found));
            }
            if (!found.isEmpty()) {
                out.addAll(found);
                return;
            }
            if (operands.stream().anyMatch(o -> o.size() != targets.size())) {
                String sizes = targetPath + "=" + targets.size();
                for (int k = 0; k < operandPaths.size(); k++) {
                    sizes += ", " + operandPaths.get(k) + "=" + operands.get(k).size();
                }
                out.add(new Violation(NAME, targetPath,
                        "paths are matched element by element but have different lengths: " + sizes));
                return;
            }
            String op = product ? " × " : " + ";
            for (int i = 0; i < targets.size(); i++) {
                BigDecimal expected = product ? BigDecimal.ONE : BigDecimal.ZERO;
                List<String> shown = new ArrayList<>();
                for (List<Num> operand : operands) {
                    BigDecimal n = operand.get(i).value();
                    expected = product ? expected.multiply(n) : expected.add(n);
                    shown.add(plain(n));
                }
                Num target = targets.get(i);
                if (differs(expected, target.value(), tolerance)) {
                    out.add(new Violation(NAME, target.path(), target.path() + " is " + plain(target.value())
                            + " but " + String.join(op, shown) + " is " + plain(expected)
                            + " (tolerance " + plain(tolerance) + ")"));
                }
            }
        }
    }

    private record Range(String path, BigDecimal min, BigDecimal max) implements Rule {

        @Override
        public void check(JsonNode output, BigDecimal tolerance, List<Violation> out) {
            for (Num n : read(output, path, out)) {
                if (n.value().compareTo(min) < 0 || n.value().compareTo(max) > 0) {
                    out.add(new Violation(NAME, n.path(), plain(n.value())
                            + " is outside [" + plain(min) + ", " + plain(max) + "]"));
                }
            }
        }
    }

    /** Thrown inside a custom rule when it reads a value that is not a usable number. */
    private static final class ReadFailure extends RuntimeException {

        private final transient Violation violation;

        ReadFailure(Violation violation) {
            super(violation.message(), null, false, false);
            this.violation = violation;
        }
    }

    private record Custom(String name, MathRule rule) implements Rule {

        @Override
        public void check(JsonNode output, BigDecimal tolerance, List<Violation> out) {
            Numbers numbers = new Numbers() {
                @Override
                public BigDecimal get(String path) {
                    List<Num> nums = numsAt(path);
                    if (nums.size() != 1) {
                        throw new ReadFailure(new Violation(NAME, path, "custom rule '" + name
                                + "': expected one value, found " + nums.size()));
                    }
                    return nums.get(0).value();
                }

                @Override
                public List<BigDecimal> all(String path) {
                    return numsAt(path).stream().map(Num::value).toList();
                }

                private List<Num> numsAt(String path) {
                    List<Violation> found = new ArrayList<>();
                    List<Num> nums = read(output, Paths.validate(path), found);
                    if (!found.isEmpty()) {
                        Violation first = found.get(0);
                        throw new ReadFailure(new Violation(NAME, first.path(),
                                "custom rule '" + name + "': " + first.message()));
                    }
                    return nums;
                }
            };
            try {
                if (!rule.test(numbers)) {
                    out.add(new Violation(NAME, "", "custom rule '" + name + "' failed"));
                }
            } catch (ReadFailure e) {
                out.add(e.violation);
            } catch (RuntimeException e) {
                out.add(new Violation(NAME, "", "custom rule '" + name + "' threw "
                        + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    /** Builds a {@link MathGate}. */
    public static final class Builder {

        private final List<Rule> rules = new ArrayList<>();
        private BigDecimal tolerance = BigDecimal.ZERO;

        private Builder() {}

        /** Requires the numbers at {@code itemsPath} to add up to the number at {@code totalPath}. */
        public Builder sumEquals(String itemsPath, String totalPath) {
            rules.add(new SumEquals(Paths.validate(itemsPath), Paths.validate(totalPath), false));
            return this;
        }

        /**
         * Like {@link #sumEquals}, for figures each rounded where they were printed (statements in
         * thousands or millions, reports with rounded percentages): the printed total may differ from
         * the sum of the printed items by up to half a unit of the last decimal place for each figure,
         * items and total. Five whole-number lines and their total may be off by 3; with one decimal
         * place, by 0.3. The gate's tolerance still applies when it is larger.
         */
        public Builder sumEqualsRounded(String itemsPath, String totalPath) {
            rules.add(new SumEquals(Paths.validate(itemsPath), Paths.validate(totalPath), true));
            return this;
        }

        /** Requires {@code totalPath} to equal the sum of {@code partPaths}; paths with {@code [*]} are matched element by element. */
        public Builder equalsSum(String totalPath, String... partPaths) {
            rules.add(new ElementWise(Paths.validate(totalPath), operands(partPaths), false));
            return this;
        }

        /** Requires {@code resultPath} to equal the product of {@code factorPaths}; paths with {@code [*]} are matched element by element. */
        public Builder productEquals(String resultPath, String... factorPaths) {
            rules.add(new ElementWise(Paths.validate(resultPath), operands(factorPaths), true));
            return this;
        }

        /** Requires every number at {@code path} to lie in {@code [min, max]}, bounds included. */
        public Builder range(String path, double min, double max) {
            return range(path, BigDecimal.valueOf(min), BigDecimal.valueOf(max));
        }

        /** Requires every number at {@code path} to lie in {@code [min, max]}, bounds included. */
        public Builder range(String path, BigDecimal min, BigDecimal max) {
            if (min == null || max == null || min.compareTo(max) > 0) {
                throw new IllegalArgumentException("range needs min <= max");
            }
            rules.add(new Range(Paths.validate(path), min, max));
            return this;
        }

        /** Adds a custom rule; {@code name} appears in the violation when it fails. */
        public Builder custom(String name, MathRule rule) {
            if (name == null || name.isBlank() || rule == null) {
                throw new IllegalArgumentException("custom rule needs a name and a rule");
            }
            rules.add(new Custom(name, rule));
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

        private static List<String> operands(String... paths) {
            if (paths == null || paths.length == 0) {
                throw new IllegalArgumentException("at least one operand path is needed");
            }
            return Arrays.stream(paths).map(Paths::validate).toList();
        }
    }
}
