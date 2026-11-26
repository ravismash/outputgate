package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProvenanceGateScaleTest {

    private static final SourceDocument IN_THOUSANDS = SourceDocument.ofPages(
            "CONDENSED CONSOLIDATED BALANCE SHEETS\n(in thousands, except per share data)\n"
                    + "Total current assets   13,226   12,004\nTotal assets   48,310   47,155");

    private static final SourceDocument NO_SCALE = SourceDocument.ofPages(
            "Order summary\nItems   3\nTotal   1,250.00");

    private static String ev(String value, int page, String text) {
        return "{\"value\": " + value + ", \"source_span\": {\"page\": " + page + ", \"text\": \"" + text + "\"}}";
    }

    private static ProvenanceGate gate() {
        return ProvenanceGate.builder()
                .require("scale", "total_assets")
                .optional("total_current_assets")
                .groundedIgnoringWhitespace()
                .scale("scale", "total_assets", "total_current_assets")
                .build();
    }

    private static Verdict run(ProvenanceGate gate, String json, SourceDocument doc) {
        return Contract.builder().gate(gate).build().evaluate(json, EvalContext.empty().withSource(doc));
    }

    private static String output(String scale, String scaleQuote, String assets, String current) {
        return "{\"scale\": " + ev(scale, 1, scaleQuote)
                + ", \"total_assets\": " + ev(assets, 1, "Total assets   48,310")
                + ", \"total_current_assets\": " + ev(current, 1, "Total current assets   13,226") + "}";
    }

    @Test
    @DisplayName("values in base units with the scale quoted from the header pass")
    void baseUnitsPass() {
        Verdict v = run(gate(), output("1000", "(in thousands, except per share data)", "48310000", "13226000"), IN_THOUSANDS);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("a value copied as printed from an \"in thousands\" table is rejected")
    void valuesAsPrintedRejected() {
        Verdict v = run(gate(), output("1000", "(in thousands, except per share data)", "48310", "13226000"), IN_THOUSANDS);
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        assertEquals("total_assets", v.violations().get(0).path());
        assertEquals("value 48310 is not a number its quote prints times the scale 1000 (it prints 48,310 (48310000 in thousands))",
                v.violations().get(0).message());
    }

    @Test
    @DisplayName("claiming scale 1 on a page that states thousands is rejected, even with the numbers as printed")
    void scaleOneOnScaledPageRejected() {
        Verdict v = run(gate(), output("1", "CONDENSED CONSOLIDATED BALANCE SHEETS", "48310", "13226"), IN_THOUSANDS);
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        assertEquals("scale", v.violations().get(0).path());
        assertTrue(v.violations().get(0).message().startsWith("scale is 1 but page 1 states figures in thousands"),
                v.violations().get(0).message());
    }

    @Test
    @DisplayName("a scale whose quote states a different scale, or none, is rejected")
    void scaleQuoteMustStateIt() {
        Verdict millions = run(gate(), output("1000000", "(in thousands, except per share data)", "48310000000", "13226000000"),
                IN_THOUSANDS);
        assertEquals("scale is 1000000 (millions) but its quote \"(in thousands, except per share data)\" says thousands",
                millions.violations().get(0).message());

        Verdict none = run(gate(), output("1000", "CONDENSED CONSOLIDATED BALANCE SHEETS", "48310000", "13226000"), IN_THOUSANDS);
        assertTrue(none.violations().get(0).message().contains("states no scale"), () -> none.violations().toString());
    }

    @Test
    @DisplayName("a scale that is not one of the named factors is rejected")
    void oddScaleRejected() {
        Verdict v = run(gate(), output("500", "(in thousands, except per share data)", "48310000", "13226000"), IN_THOUSANDS);
        assertEquals("scale", v.violations().get(0).path());
        assertTrue(v.violations().get(0).message().startsWith("scale must be 1, 1000"), v.violations().get(0).message());
    }

    @Test
    @DisplayName("a scale quote that is not on the page is rejected by grounding")
    void scaleQuoteGrounded() {
        Verdict v = run(gate(), output("1000", "(in thousands)", "48310000", "13226000"), NO_SCALE);
        assertTrue(v.violations().stream().anyMatch(x -> x.path().equals("scale") && x.message().contains("not found")),
                () -> v.violations().toString());
    }

    @Test
    @DisplayName("scale 1 passes on a page that states no scale")
    void scaleOneWithoutHeaderPasses() {
        ProvenanceGate g = ProvenanceGate.builder().require("scale", "total").groundedIgnoringWhitespace().scale("scale", "total").build();
        String json = "{\"scale\": " + ev("1", 1, "Order summary") + ", \"total\": " + ev("1250", 1, "Total   1,250.00") + "}";
        Verdict v = run(g, json, NO_SCALE);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("a missing scale field blocks, like any required field")
    void missingScaleBlocks() {
        String json = "{\"total_assets\": " + ev("48310000", 1, "Total assets   48,310") + "}";
        Verdict v = run(gate(), json, IN_THOUSANDS);
        assertEquals(Status.REJECTED, v.status());
        assertEquals("scale", v.violations().get(0).path());
    }

    @Test
    @DisplayName("an optional value field that is absent is fine; values work without grounding")
    void optionalAndUngrounded() {
        ProvenanceGate g = ProvenanceGate.builder().require("scale", "total_assets").optional("total_current_assets")
                .scale("scale", "total_assets", "total_current_assets").build();
        String json = "{\"scale\": " + ev("1e6", 1, "(Dollars in millions)") + ", \"total_assets\": "
                + ev("48310000000", 1, "Total assets $ 48,310") + "}";
        Verdict v = Contract.builder().gate(g).build().evaluate(json, EvalContext.empty());
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"(in thousands)", "($ in thousands)", "(In Thousands of U.S. Dollars)", "in USD thousands",
            "(thousands)", "(Rs. in thousands)", "Amounts in thousands"})
    @DisplayName("common ways of stating thousands are read as 1000")
    void thousandsPhrasings(String quote) {
        ProvenanceGate g = ProvenanceGate.builder().require("scale", "total").scale("scale", "total").build();
        String json = "{\"scale\": " + ev("1000", 1, quote) + ", \"total\": " + ev("5000", 1, "Total 5") + "}";
        Verdict v = Contract.builder().gate(g).build().evaluate(json, EvalContext.empty());
        assertTrue(v.passed(), () -> quote + ": " + v.violations());
    }

    @Test
    @DisplayName("lakhs and crores are read too")
    void indianScales() {
        ProvenanceGate g = ProvenanceGate.builder().require("scale", "total").scale("scale", "total").build();
        String json = "{\"scale\": " + ev("10000000", 1, "(Rs. in crores)") + ", \"total\": " + ev("125000000", 1, "Total 12.50") + "}";
        Verdict v = Contract.builder().gate(g).build().evaluate(json, EvalContext.empty());
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("prose like \"thousands of customers\" is not a scale statement")
    void proseIsNotAScale() {
        SourceDocument doc = SourceDocument.ofPages("We serve thousands of customers.\nTotal   1,250");
        ProvenanceGate g = ProvenanceGate.builder().require("scale", "total").grounded().scale("scale", "total").build();
        String json = "{\"scale\": " + ev("1", 1, "We serve thousands of customers.") + ", \"total\": " + ev("1250", 1, "Total   1,250") + "}";
        Verdict v = run(g, json, doc);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("fields outside the scale still use the unscaled numbersMatchQuotes check")
    void unscaledFieldsUnchanged() {
        ProvenanceGate g = ProvenanceGate.builder().require("scale", "total", "shares").numbersMatchQuotes()
                .scale("scale", "total").build();
        String json = "{\"scale\": " + ev("1000", 1, "(in thousands)") + ", \"total\": " + ev("5000", 1, "Total 5")
                + ", \"shares\": " + ev("12", 1, "Shares 12") + "}";
        Verdict v = Contract.builder().gate(g).build().evaluate(json, EvalContext.empty());
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("build rejects a scale field that isn't required, uses [*], or names unchecked values")
    void buildValidation() {
        assertThrows(IllegalStateException.class,
                () -> ProvenanceGate.builder().require("total").optional("scale").scale("scale", "total").build());
        assertThrows(IllegalStateException.class,
                () -> ProvenanceGate.builder().require("total", "s[*]").scale("s[*]", "total").build());
        assertThrows(IllegalStateException.class,
                () -> ProvenanceGate.builder().require("scale", "total").scale("scale", "other").build());
        assertThrows(IllegalArgumentException.class,
                () -> ProvenanceGate.builder().require("scale").scale("scale"));
    }
}
