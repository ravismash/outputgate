package io.github.ravismash.outputgate.junit;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One line of a JSONL golden set (R12).
 *
 * @param name the test name shown in the JUnit report
 * @param output the recorded model output, as raw text
 * @param input the prompt or document the output came from, if recorded (used by a runner later)
 * @param expected fields the output must contain, read through evidence objects; null when none
 * @param expectReject the gate that must reject the output, {@code "*"} for any gate, or null when it must pass
 */
public record GoldenCase(String name, String output, String input, JsonNode expected, String expectReject) {

    /** @return true when this case must be rejected */
    public boolean mustReject() {
        return expectReject != null;
    }

    @Override
    public String toString() {
        return name;
    }
}
