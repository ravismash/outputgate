package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegexGateTest {

    private static final String APOLOGY = "(?i)\\b(sorry|apologi[sz]e|as an ai)\\b";

    private static Verdict run(RegexGate gate, String json) {
        return Contract.builder().gate(gate).build().evaluate(json);
    }

    private static String items(String... descriptions) {
        var b = new StringBuilder("{\"line_items\": [");
        for (int i = 0; i < descriptions.length; i++) {
            b.append(i > 0 ? ", " : "").append("{\"description\": ").append(descriptions[i]).append('}');
        }
        return b.append("]}").toString();
    }

    @Test
    @DisplayName("R8: an apology phrase in a description field is rejected")
    void apologyRejected() {
        RegexGate gate = RegexGate.builder().forbid("line_items[*].description", APOLOGY).build();
        Verdict v = run(gate, items("\"Widgets x 2\"", "\"I'm sorry, I could not read this line\""));
        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        Violation violation = v.violations().get(0);
        assertEquals("regex", violation.gate());
        assertEquals("line_items[1].description", violation.path());
        assertTrue(violation.message().contains("sorry"), violation.message());
    }

    @Test
    @DisplayName("R8/R9: forbid reads through evidence objects")
    void forbidOnEvidence() {
        RegexGate gate = RegexGate.builder().forbid("line_items[*].description", APOLOGY).build();
        String ev = "{\"value\": \"As an AI, I cannot\", \"source_span\": {\"page\": 1, \"text\": \"x\"}}";
        Verdict v = run(gate, items(ev));
        assertEquals(List.of("line_items[0].description"), v.violations().stream().map(Violation::path).toList());
        assertTrue(v.violations().get(0).message().contains("As an AI"), v.violations().toString());
    }

    @Test
    @DisplayName("R8: clean text passes forbid, and a missing field has nothing to forbid")
    void forbidPassesCleanAndMissing() {
        RegexGate gate = RegexGate.builder().forbid("line_items[*].description", APOLOGY).build();
        assertTrue(run(gate, items("\"Widgets x 2\"")).passed());
        assertTrue(run(gate, "{\"line_items\": [{}]}").passed());
    }

    @Test
    @DisplayName("R8: require needs the whole value to match")
    void requireWholeValue() {
        RegexGate gate = RegexGate.builder().require("invoice_number", "INV-\\d{4}").build();
        assertTrue(run(gate, "{\"invoice_number\": \"INV-2041\"}").passed());
        Verdict v = run(gate, "{\"invoice_number\": \"Invoice INV-2041\"}");
        assertEquals("invoice_number", v.violations().get(0).path());
        assertTrue(v.violations().get(0).message().contains("does not match"), v.violations().toString());
    }

    @Test
    @DisplayName("R8: require on a missing or non-text value is a violation (fail-closed)")
    void requireMissingOrNotText() {
        RegexGate gate = RegexGate.builder().require("invoice_number", "INV-\\d{4}").build();
        Violation missing = run(gate, "{}").violations().get(0);
        assertEquals("invoice_number", missing.path());
        assertTrue(missing.message().contains("no value"), missing.message());
        Violation number = run(gate, "{\"invoice_number\": 2041}").violations().get(0);
        assertEquals("invoice_number", number.path());
        assertTrue(number.message().contains("not text"), number.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-02", "2024-02-29"})
    @DisplayName("R8: isoDate accepts real yyyy-MM-dd dates")
    void isoDateAccepts(String date) {
        assertTrue(run(RegexGate.builder().isoDate("date").build(), "{\"date\": \"" + date + "\"}").passed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026/10/02", "02-10-2026", "2026-13-01", "2026-02-30", "2025-02-29", "2026-10-2"})
    @DisplayName("R8: isoDate rejects other formats and impossible dates")
    void isoDateRejects(String date) {
        Verdict v = run(RegexGate.builder().isoDate("date").build(), "{\"date\": \"" + date + "\"}");
        assertEquals(List.of("date"), v.violations().stream().map(Violation::path).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "INR", "JPY"})
    @DisplayName("R8: currencyCode accepts ISO 4217 codes")
    void currencyAccepts(String code) {
        assertTrue(run(RegexGate.builder().currencyCode("currency").build(), "{\"currency\": \"" + code + "\"}").passed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "US$", "Rs", "XYZ", "DOLLARS", "€"})
    @DisplayName("R8: currencyCode rejects symbols, lower case and made-up codes")
    void currencyRejects(String code) {
        Verdict v = run(RegexGate.builder().currencyCode("currency").build(), "{\"currency\": \"" + code + "\"}");
        assertEquals(List.of("currency"), v.violations().stream().map(Violation::path).toList());
        assertTrue(v.violations().get(0).message().contains("ISO 4217"), v.violations().toString());
    }

    @Test
    @DisplayName("R8: several rules report every violation")
    void severalRules() {
        RegexGate gate = RegexGate.builder().isoDate("date").currencyCode("currency").build();
        Verdict v = run(gate, "{\"date\": \"02/10/2026\", \"currency\": \"$\"}");
        assertEquals(List.of("date", "currency"), v.violations().stream().map(Violation::path).toList());
    }

    @Test
    @DisplayName("R8: an empty gate or an invalid pattern is rejected at build")
    void badConfigRejected() {
        assertThrows(IllegalStateException.class, () -> RegexGate.builder().build());
        assertThrows(IllegalArgumentException.class, () -> RegexGate.builder().forbid("a", "(unclosed"));
        assertThrows(IllegalArgumentException.class, () -> RegexGate.builder().require("$.a", "x"));
    }
}
