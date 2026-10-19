package io.github.ravismash.outputgate.junit;

import static io.github.ravismash.outputgate.junit.Contracts.INVOICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoldenSetTest {

    @Test
    @DisplayName("R12: loads every case in file order, skipping blank lines")
    void loadsCases() {
        List<GoldenCase> cases = GoldenSet.load("golden/sample.jsonl");
        assertEquals(List.of("clean invoice", "clean invoice with evidence", "dropped line item", "chatty prose"),
                cases.stream().map(GoldenCase::name).toList());
        assertNull(cases.get(0).expectReject());
        assertEquals("math", cases.get(2).expectReject());
        assertTrue(cases.get(0).output().startsWith("{"), "an object output is kept as JSON text");
        assertEquals("Sure! Here is the invoice: {\"subtotal\": 1}", cases.get(3).output());
    }

    @ParameterizedTest
    @ValueSource(strings = {"golden/bad-json.jsonl:2", "golden/no-name.jsonl:1", "golden/duplicate.jsonl:2",
        "golden/both.jsonl:1", "golden/no-output.jsonl:1"})
    @DisplayName("R12: a bad golden file is rejected with the line number")
    void badFilesNameTheLine(String resourceAndLine) {
        String[] parts = resourceAndLine.split(":");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> GoldenSet.load(parts[0]));
        assertTrue(e.getMessage().contains(parts[0] + " line " + parts[1]), e.getMessage());
    }

    @Test
    @DisplayName("R12: a missing golden file is a clear error")
    void missingFile() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> GoldenSet.load("golden/nope.jsonl"));
        assertTrue(e.getMessage().contains("golden/nope.jsonl"), e.getMessage());
    }

    @Test
    @DisplayName("R12: a case that should be rejected but passes fails the test")
    void expectedRejectionThatPassesFails() {
        GoldenCase c = new GoldenCase("wrongly expected", GoldenCases.CLEAN, null, null, "math");
        AssertionError e = assertThrows(AssertionError.class, () -> LlmAssert.assertThat(c).matches(INVOICE));
        assertTrue(e.getMessage().contains("wrongly expected") && e.getMessage().contains("PASSED"), e.getMessage());
    }

    @Test
    @DisplayName("R12: an expected field with a different value fails the test with both values")
    void expectedMismatchFails() {
        GoldenCase c = new GoldenCase("wrong vendor", GoldenCases.CLEAN, null,
                GoldenCases.json("{\"vendor\": \"Globex\", \"subtotal\": 74.50}"), null);
        AssertionError e = assertThrows(AssertionError.class, () -> LlmAssert.assertThat(c).matches(INVOICE));
        assertTrue(e.getMessage().contains("vendor") && e.getMessage().contains("Globex") && e.getMessage().contains("Acme"),
                e.getMessage());
    }

    @Test
    @DisplayName("R12: an expected field missing from the output fails the test")
    void expectedMissingFails() {
        GoldenCase c = new GoldenCase("missing currency", GoldenCases.CLEAN, null, GoldenCases.json("{\"currency\": \"USD\"}"), null);
        AssertionError e = assertThrows(AssertionError.class, () -> LlmAssert.assertThat(c).matches(INVOICE));
        assertTrue(e.getMessage().contains("currency"), e.getMessage());
    }

    @Test
    @DisplayName("R12: numbers in expected compare by value (74.5 equals 74.50)")
    void numbersCompareByValue() {
        GoldenCase c = new GoldenCase("by value", GoldenCases.CLEAN, null, GoldenCases.json("{\"subtotal\": 74.5}"), null);
        LlmAssert.assertThat(c).matches(INVOICE);
    }

    @Test
    @DisplayName("R12: expect_reject \"*\" accepts a rejection by any gate")
    void anyGate() {
        GoldenCase c = new GoldenCase("any", "not json", null, null, "*");
        LlmAssert.assertThat(c).matches(INVOICE);
    }
}
