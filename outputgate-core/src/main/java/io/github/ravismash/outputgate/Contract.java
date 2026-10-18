package io.github.ravismash.outputgate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of gates. Evaluation is fail-closed and runs every gate (R1);
 * output that is not JSON is rejected before any gate runs (R2).
 */
public final class Contract {

    /** Name of the result reported when the output is not JSON. */
    public static final String JSON_PARSE = "json-parse";

    private final List<Gate> gates;

    private Contract(List<Gate> gates) {
        this.gates = List.copyOf(gates);
    }

    /** Starts a new contract. */
    public static Builder builder() {
        return new Builder();
    }

    /** Evaluates raw model text with an empty context. */
    public Verdict evaluate(String rawOutput) {
        return evaluate(rawOutput, EvalContext.empty());
    }

    /** Evaluates raw model text. */
    public Verdict evaluate(String rawOutput, EvalContext context) {
        JsonNode output;
        try {
            output = Json.parse(rawOutput);
        } catch (JsonProcessingException e) {
            Violation v = new Violation(JSON_PARSE, "", "output is not JSON: " + e.getOriginalMessage());
            return new Verdict(Status.REJECTED, List.of(new GateResult(JSON_PARSE, false, List.of(v))));
        }

        List<GateResult> results = new ArrayList<>(gates.size());
        for (Gate gate : gates) {
            results.add(run(gate, output, context));
        }
        boolean allPassed = results.stream().allMatch(GateResult::passed);
        return new Verdict(allPassed ? Status.PASSED : Status.REJECTED, results);
    }

    private static GateResult run(Gate gate, JsonNode output, EvalContext context) {
        String name = gate.name();
        List<Violation> violations;
        try {
            violations = gate.check(output, context);
        } catch (RuntimeException e) {
            String message = "gate threw " + e.getClass().getSimpleName() + ": " + e.getMessage();
            return new GateResult(name, false, List.of(new Violation(name, "", message)));
        }
        if (violations == null) {
            return new GateResult(name, false, List.of(new Violation(name, "", "gate returned null")));
        }
        return new GateResult(name, violations.isEmpty(), violations);
    }

    /** Builds a {@link Contract}. */
    public static final class Builder {

        private final List<Gate> gates = new ArrayList<>();

        private Builder() {}

        /** Adds a gate; gates run in the order they are added. */
        public Builder gate(Gate gate) {
            if (gate == null) {
                throw new IllegalArgumentException("gate is null");
            }
            gates.add(gate);
            return this;
        }

        /** Builds the contract. */
        public Contract build() {
            return new Contract(gates);
        }
    }
}
