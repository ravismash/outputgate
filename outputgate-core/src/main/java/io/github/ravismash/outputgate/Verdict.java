package io.github.ravismash.outputgate;

import java.util.List;

/**
 * The result of evaluating one output against a contract.
 *
 * @param status PASSED or REJECTED
 * @param results one result per gate, in contract order
 */
public record Verdict(Status status, List<GateResult> results) {

    /** Copies the list so a verdict cannot change after it is made. */
    public Verdict {
        results = List.copyOf(results);
    }

    /** True when the status is {@link Status#PASSED}. */
    public boolean passed() {
        return status == Status.PASSED;
    }

    /** Every violation from every gate, in contract order. */
    public List<Violation> violations() {
        return results.stream().flatMap(r -> r.violations().stream()).toList();
    }
}
