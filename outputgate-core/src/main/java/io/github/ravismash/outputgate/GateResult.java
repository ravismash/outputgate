package io.github.ravismash.outputgate;

import java.util.List;

/**
 * The outcome of one gate for one output.
 *
 * @param gate the gate name
 * @param passed true only when the gate ran and found no violation
 * @param violations everything the gate found
 */
public record GateResult(String gate, boolean passed, List<Violation> violations) {

    /** Copies the list so a result cannot change after it is made. */
    public GateResult {
        violations = List.copyOf(violations);
    }
}
