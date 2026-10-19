package io.github.ravismash.outputgate.junit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.GateResult;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.Violation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JUnit assertions for LLM output against a {@link Contract}:
 * {@code assertThat(output).passes(contract)} and
 * {@code assertThat(output).isRejectedBy(contract).byGate("math")}.
 * A failure lists every violation.
 */
public final class LlmAssert {

    private LlmAssert() {}

    /** @param rawOutput the model's raw text @return assertions on it */
    public static OutputAssert assertThat(String rawOutput) {
        return new OutputAssert(LlmOutput.of(rawOutput), EvalContext.empty());
    }

    /** @param output the model's raw text and usage @return assertions on it */
    public static OutputAssert assertThat(LlmOutput output) {
        return new OutputAssert(output, EvalContext.empty());
    }

    /** @param golden a golden case @return assertions on it */
    public static GoldenAssert assertThat(GoldenCase golden) {
        return new GoldenAssert(golden, EvalContext.empty());
    }

    /** Assertions on one output. */
    public static final class OutputAssert {

        private final LlmOutput output;
        private final EvalContext context;

        private OutputAssert(LlmOutput output, EvalContext context) {
            this.output = output;
            this.context = context;
        }

        /** @param context e.g. a source document for grounding @return assertions with that context */
        public OutputAssert withContext(EvalContext context) {
            return new OutputAssert(output, context);
        }

        /** @param contract the contract @return the verdict, which passed */
        public Verdict passes(Contract contract) {
            Verdict v = contract.evaluate(output, context);
            if (!v.passed()) {
                throw new AssertionError("expected the output to pass the contract, but it was REJECTED:\n" + list(v));
            }
            return v;
        }

        /** @param contract the contract @return assertions on the rejection */
        public RejectionAssert isRejectedBy(Contract contract) {
            Verdict v = contract.evaluate(output, context);
            if (v.passed()) {
                throw new AssertionError("expected the output to be rejected, but it PASSED");
            }
            return new RejectionAssert(v);
        }
    }

    /** Assertions on a rejection. */
    public static final class RejectionAssert {

        private final Verdict verdict;

        private RejectionAssert(Verdict verdict) {
            this.verdict = verdict;
        }

        /** @param gate a gate name, e.g. {@code "math"} or {@code "json-parse"} @return this */
        public RejectionAssert byGate(String gate) {
            List<String> failing = failingGates(verdict);
            if (!failing.contains(gate)) {
                throw new AssertionError("expected rejection by gate '" + gate + "', but the failing gates were "
                        + failing + ":\n" + list(verdict));
            }
            return this;
        }

        /** @return the verdict */
        public Verdict verdict() {
            return verdict;
        }
    }

    /** Assertions on a golden case. */
    public static final class GoldenAssert {

        private final GoldenCase golden;
        private final EvalContext context;

        private GoldenAssert(GoldenCase golden, EvalContext context) {
            this.golden = golden;
            this.context = context;
        }

        /** @param context e.g. a source document for grounding @return assertions with that context */
        public GoldenAssert withContext(EvalContext context) {
            return new GoldenAssert(golden, context);
        }

        /**
         * Passes when the case behaves as recorded: rejected by {@code expect_reject}'s gate,
         * or passing with every {@code expected} field present and equal (numbers by value,
         * evidence objects read as their value).
         *
         * @param contract the contract
         * @return the verdict
         */
        public Verdict matches(Contract contract) {
            Outcome outcome = evaluate(golden, contract, context);
            if (outcome.problem() != null) {
                throw new AssertionError("golden case \"" + golden.name() + "\": " + outcome.problem());
            }
            return outcome.verdict();
        }
    }

    /** A golden case's verdict, and why it didn't behave as recorded (null when it did). */
    record Outcome(Verdict verdict, String problem) {}

    /** Checks one golden case without throwing; shared by {@link GoldenAssert} and {@link GoldenRunner}. */
    static Outcome evaluate(GoldenCase golden, Contract contract, EvalContext context) {
        Verdict v = contract.evaluate(LlmOutput.of(golden.output()), context);
        if (golden.mustReject()) {
            String gate = golden.expectReject();
            if (v.passed()) {
                return new Outcome(v, "expected rejection" + ("*".equals(gate) ? "" : " by gate '" + gate + "'")
                        + ", but it PASSED");
            }
            if (!"*".equals(gate) && !failingGates(v).contains(gate)) {
                return new Outcome(v, "expected rejection by gate '" + gate + "', but the failing gates were "
                        + failingGates(v) + ":\n" + list(v));
            }
            return new Outcome(v, null);
        }
        if (!v.passed()) {
            return new Outcome(v, "expected to pass, but it was REJECTED:\n" + list(v));
        }
        if (golden.expected() != null) {
            List<String> problems = new ArrayList<>();
            try {
                compare("", golden.expected(), StrictJson.parse(golden.output()), problems);
            } catch (JsonProcessingException e) {
                problems.add("output is not JSON: " + e.getOriginalMessage());
            }
            if (!problems.isEmpty()) {
                return new Outcome(v, "output differs from expected:\n  " + String.join("\n  ", problems));
            }
        }
        return new Outcome(v, null);
    }

    private static List<String> failingGates(Verdict v) {
        return v.results().stream().filter(r -> !r.passed()).map(GateResult::gate).toList();
    }

    private static String list(Verdict v) {
        StringBuilder b = new StringBuilder();
        for (Violation x : v.violations()) {
            b.append("  [").append(x.gate()).append("] ")
                    .append(x.path().isEmpty() ? "(whole output)" : x.path())
                    .append(": ").append(x.message()).append('\n');
        }
        return b.toString().stripTrailing();
    }

    /** Every field in {@code expected} must be in {@code actual} with an equal value. */
    private static void compare(String path, JsonNode expected, JsonNode actual, List<String> problems) {
        actual = unwrap(actual);
        if (actual == null || actual.isMissingNode()) {
            problems.add(name(path) + ": expected " + expected + " but it is missing");
        } else if (expected.isObject()) {
            if (!actual.isObject()) {
                problems.add(name(path) + ": expected an object but was " + actual);
                return;
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = expected.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                compare(path.isEmpty() ? e.getKey() : path + "." + e.getKey(), e.getValue(), actual.path(e.getKey()), problems);
            }
        } else if (expected.isArray()) {
            if (!actual.isArray() || actual.size() != expected.size()) {
                problems.add(name(path) + ": expected " + expected + " but was " + actual);
                return;
            }
            for (int i = 0; i < expected.size(); i++) {
                compare(path + "[" + i + "]", expected.get(i), actual.get(i), problems);
            }
        } else if (expected.isNumber() && actual.isNumber()) {
            if (expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
                problems.add(name(path) + ": expected " + expected + " but was " + actual);
            }
        } else if (!expected.equals(actual)) {
            problems.add(name(path) + ": expected " + expected + " but was " + actual);
        }
    }

    private static JsonNode unwrap(JsonNode node) {
        return node != null && node.isObject() && node.has("value") ? node.get("value") : node;
    }

    private static String name(String path) {
        return path.isEmpty() ? "(whole output)" : path;
    }
}
