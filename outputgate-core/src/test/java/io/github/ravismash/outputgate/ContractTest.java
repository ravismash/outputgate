package io.github.ravismash.outputgate;

import static io.github.ravismash.outputgate.TestGates.calls;
import static io.github.ravismash.outputgate.TestGates.failing;
import static io.github.ravismash.outputgate.TestGates.passing;
import static io.github.ravismash.outputgate.TestGates.throwing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractTest {

    private static final String VALID = "{\"subtotal\": 10}";

    @Test
    @DisplayName("R1: verdict for an output failing two gates lists both")
    void listsEveryFailingGate() {
        var calls = calls();
        Verdict v = Contract.builder()
                .gate(failing("first", calls))
                .gate(failing("second", calls))
                .build()
                .evaluate(VALID);

        assertEquals(Status.REJECTED, v.status());
        assertEquals(List.of("first", "second"),
                v.violations().stream().map(Violation::gate).toList());
    }

    @Test
    @DisplayName("R1: a gate that throws is recorded as a failure, never a pass")
    void throwingGateIsAFailure() {
        var calls = calls();
        Verdict v = Contract.builder()
                .gate(throwing("explodes", calls))
                .build()
                .evaluate(VALID);

        assertEquals(Status.REJECTED, v.status());
        GateResult r = v.results().get(0);
        assertEquals("explodes", r.gate());
        assertFalse(r.passed());
        assertEquals(1, r.violations().size());
        assertTrue(r.violations().get(0).message().contains("boom"));
    }

    @Test
    @DisplayName("R1: gates run in order, and all of them run after the first failure")
    void runsEveryGateInOrder() {
        var calls = calls();
        Verdict v = Contract.builder()
                .gate(failing("a", calls))
                .gate(throwing("b", calls))
                .gate(passing("c", calls))
                .build()
                .evaluate(VALID);

        assertEquals(List.of("a", "b", "c"), calls);
        assertEquals(List.of("a", "b", "c"), v.results().stream().map(GateResult::gate).toList());
        assertEquals(List.of(false, false, true), v.results().stream().map(GateResult::passed).toList());
    }

    @Test
    @DisplayName("R1: an output passes only when every gate passes")
    void passesWhenAllGatesPass() {
        var calls = calls();
        Verdict v = Contract.builder()
                .gate(passing("a", calls))
                .gate(passing("b", calls))
                .build()
                .evaluate(VALID);

        assertTrue(v.passed());
        assertTrue(v.violations().isEmpty());
    }

    @Test
    @DisplayName("R2: chatty prose around a code block is REJECTED with one result, json-parse")
    void chattyProseIsRejectedBeforeGates() {
        String chatty = """
                Sure! Here is the extracted invoice:
                ```json
                {"subtotal": 10}
                ```
                Let me know if you need anything else.""";
        var calls = calls();
        Verdict v = Contract.builder()
                .gate(passing("never", calls))
                .build()
                .evaluate(chatty);

        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.results().size());
        assertEquals("json-parse", v.results().get(0).gate());
        assertTrue(calls.isEmpty(), "no gate may run on unparseable output");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not json", "{\"subtotal\": 10", "{\"subtotal\": 10} trailing", "{\"subtotal\": 10, \"subtotal\": 99}"})
    @DisplayName("R2: empty, plain, truncated or trailing-text or duplicate-key output is rejected as json-parse")
    void otherNonJsonIsRejected(String raw) {
        var calls = calls();
        Verdict v = Contract.builder()
                .gate(passing("never", calls))
                .build()
                .evaluate(raw);

        assertEquals(Status.REJECTED, v.status());
        assertEquals(List.of("json-parse"), v.results().stream().map(GateResult::gate).toList());
        assertTrue(calls.isEmpty());
    }
}
