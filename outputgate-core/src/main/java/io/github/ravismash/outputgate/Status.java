package io.github.ravismash.outputgate;

/** Whether an output passed every gate. */
public enum Status {
    /** Every gate ran and passed. */
    PASSED,
    /** At least one gate failed, threw, or the output was not JSON. */
    REJECTED
}
