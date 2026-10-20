package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProvenanceGateTest {

    private static final SourceDocument INVOICE = SourceDocument.ofPages(
            "ACME SUPPLIES LTD\nInvoice no. INV-2041\nDate: 2026-10-02",
            "Widgets x 2   40.00\nBolts x 1   34.50\nSubtotal   74.50");

    private static final EvalContext WITH_SOURCE = EvalContext.empty().withSource(INVOICE);

    private static String ev(String value, int page, String text) {
        return "{\"value\": " + value + ", \"source_span\": {\"page\": " + page + ", \"text\": \"" + text + "\"}}";
    }

    private static ProvenanceGate vendorAndSubtotal() {
        return ProvenanceGate.builder().require("vendor", "subtotal").build();
    }

    private static ProvenanceGate groundedVendorAndSubtotal() {
        return ProvenanceGate.builder().require("vendor", "subtotal").grounded().build();
    }

    private static Verdict run(ProvenanceGate gate, String json, EvalContext context) {
        return Contract.builder().gate(gate).build().evaluate(json, context);
    }

    private static final String GOOD = "{\"vendor\": " + ev("\"Acme Supplies Ltd\"", 1, "ACME SUPPLIES LTD")
            + ", \"subtotal\": " + ev("74.50", 2, "Subtotal   74.50") + "}";

    private static final String FABRICATED = "{\"vendor\": " + ev("\"Acme Supplies Ltd\"", 1, "ACME SUPPLIES LTD")
            + ", \"subtotal\": " + ev("100.00", 2, "Subtotal 100.00") + "}";

    @Test
    @DisplayName("R6: a required field given as a bare value is rejected")
    void bareValueRejected() {
        String json = "{\"vendor\": \"Acme Supplies Ltd\", \"subtotal\": " + ev("74.50", 2, "Subtotal   74.50") + "}";
        Verdict v = run(vendorAndSubtotal(), json, EvalContext.empty());
        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        Violation violation = v.violations().get(0);
        assertEquals("provenance", violation.gate());
        assertEquals("vendor", violation.path());
        assertTrue(violation.message().contains("source_span"), violation.message());
    }

    @Test
    @DisplayName("R6: a fabricated span is rejected when grounding is on")
    void fabricatedSpanRejectedWhenGrounded() {
        Verdict v = run(groundedVendorAndSubtotal(), FABRICATED, WITH_SOURCE);
        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        assertEquals("subtotal", v.violations().get(0).path());
        assertTrue(v.violations().get(0).message().contains("Subtotal 100.00"), v.violations().get(0).message());
    }

    @Test
    @DisplayName("R6: the same fabricated output passes when grounding is off")
    void sameOutputPassesUngrounded() {
        Verdict v = run(vendorAndSubtotal(), FABRICATED, WITH_SOURCE);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("R6: spans that really occur in the document pass grounding")
    void realSpansPassGrounded() {
        Verdict v = run(groundedVendorAndSubtotal(), GOOD, WITH_SOURCE);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("R6: grounding compares text literally (case matters)")
    void groundingIsLiteral() {
        String json = "{\"vendor\": " + ev("\"Acme\"", 1, "Acme Supplies Ltd") + ", \"subtotal\": "
                + ev("74.50", 2, "Subtotal   74.50") + "}";
        Verdict v = run(groundedVendorAndSubtotal(), json, WITH_SOURCE);
        assertEquals(List.of("vendor"), v.violations().stream().map(Violation::path).toList());
    }

    @Test
    @DisplayName("R6: a quote that spans a line break fails literal grounding but passes ignoring whitespace")
    void whitespaceIgnoredOnlyWhenAsked() {
        String json = "{\"vendor\": " + ev("\"Acme Supplies Ltd\"", 1, "ACME SUPPLIES LTD Invoice no. INV-2041")
                + ", \"subtotal\": " + ev("74.50", 2, "Subtotal 74.50") + "}";
        Verdict literal = run(groundedVendorAndSubtotal(), json, WITH_SOURCE);
        assertEquals(List.of("vendor", "subtotal"), literal.violations().stream().map(Violation::path).toList());

        ProvenanceGate relaxed = ProvenanceGate.builder().require("vendor", "subtotal").groundedIgnoringWhitespace().build();
        Verdict v = run(relaxed, json, WITH_SOURCE);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"subtotal 74.50", "Subtotal 74,50", "Subtotal $74.50", "Subtotal 75.50"})
    @DisplayName("R6: ignoring whitespace still requires case, punctuation, currency signs and digits to match")
    void onlyWhitespaceIsIgnored(String quote) {
        ProvenanceGate relaxed = ProvenanceGate.builder().require("subtotal").groundedIgnoringWhitespace().build();
        Verdict v = run(relaxed, "{\"subtotal\": " + ev("74.50", 2, quote) + "}", WITH_SOURCE);
        assertEquals(List.of("subtotal"), v.violations().stream().map(Violation::path).toList());
        assertTrue(v.violations().get(0).message().contains("whitespace ignored"), v.violations().toString());
    }

    @Test
    @DisplayName("R6: a quote that is only whitespace is rejected, not matched against everything")
    void whitespaceOnlyQuoteRejected() {
        ProvenanceGate relaxed = ProvenanceGate.builder().require("subtotal").groundedIgnoringWhitespace().build();
        Verdict v = run(relaxed, "{\"subtotal\": " + ev("74.50", 2, "   ") + "}", WITH_SOURCE);
        assertEquals(Status.REJECTED, v.status());
        assertTrue(v.violations().get(0).message().contains("only whitespace"), v.violations().toString());
    }

    @Test
    @DisplayName("R6: non-breaking spaces in PDF text count as whitespace")
    void nonBreakingSpaces() {
        ProvenanceGate relaxed = ProvenanceGate.builder().require("subtotal").groundedIgnoringWhitespace().build();
        EvalContext nbsp = EvalContext.empty().withSource(SourceDocument.of("Subtotal\u00A0\u00A074.50"));
        Verdict v = run(relaxed, "{\"subtotal\": " + ev("74.50", 1, "Subtotal 74.50") + "}", nbsp);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("R6: grounding on but no source document is a violation, not a pass")
    void groundingWithoutSourceFails() {
        Verdict v = run(groundedVendorAndSubtotal(), GOOD, EvalContext.empty());
        assertEquals(Status.REJECTED, v.status());
        assertTrue(v.violations().get(0).message().contains("no source document"), v.violations().toString());
    }

    @Test
    @DisplayName("R6: a missing required field is a violation")
    void missingFieldFails() {
        String json = "{\"vendor\": " + ev("\"Acme Supplies Ltd\"", 1, "ACME SUPPLIES LTD") + "}";
        Verdict v = run(vendorAndSubtotal(), json, EvalContext.empty());
        assertEquals(List.of("subtotal"), v.violations().stream().map(Violation::path).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"value\": 74.50}",
        "{\"value\": 74.50, \"source_span\": \"page 2\"}",
        "{\"value\": 74.50, \"source_span\": {\"text\": \"Subtotal\"}}",
        "{\"value\": 74.50, \"source_span\": {\"page\": 0, \"text\": \"Subtotal\"}}",
        "{\"value\": 74.50, \"source_span\": {\"page\": \"2\", \"text\": \"Subtotal\"}}",
        "{\"value\": 74.50, \"source_span\": {\"page\": 2, \"text\": \"\"}}",
        "{\"value\": 74.50, \"source_span\": {\"page\": 2, \"text\": 7450}}",
        "{\"source_span\": {\"page\": 2, \"text\": \"Subtotal\"}}"
    })
    @DisplayName("R6: a malformed evidence object is rejected")
    void malformedEvidenceRejected(String subtotal) {
        String json = "{\"vendor\": " + ev("\"Acme Supplies Ltd\"", 1, "ACME SUPPLIES LTD") + ", \"subtotal\": " + subtotal + "}";
        Verdict v = run(vendorAndSubtotal(), json, EvalContext.empty());
        assertEquals(List.of("subtotal"), v.violations().stream().map(Violation::path).toList());
    }

    @Test
    @DisplayName("R6: [*] paths check every element and report the one that fails")
    void everyElementChecked() {
        ProvenanceGate gate = ProvenanceGate.builder().require("line_items[*].amount").grounded().build();
        String json = "{\"line_items\": ["
                + "{\"amount\": " + ev("40.00", 2, "Widgets x 2   40.00") + "},"
                + "{\"amount\": 34.50},"
                + "{\"amount\": " + ev("12.00", 2, "Nuts x 3   12.00") + "}]}";
        Verdict v = run(gate, json, WITH_SOURCE);
        assertEquals(List.of("line_items[1].amount", "line_items[2].amount"),
                v.violations().stream().map(Violation::path).toList());
    }

    @Test
    @DisplayName("R6: a real quote cited with the wrong page is rejected, and the message names the right page")
    void wrongPageRejected() {
        String json = "{\"vendor\": " + ev("\"Acme Supplies Ltd\"", 1, "ACME SUPPLIES LTD")
                + ", \"subtotal\": " + ev("74.50", 1, "Subtotal   74.50") + "}";
        for (ProvenanceGate gate : List.of(groundedVendorAndSubtotal(),
                ProvenanceGate.builder().require("vendor", "subtotal").groundedIgnoringWhitespace().build())) {
            Verdict v = run(gate, json, WITH_SOURCE);
            assertEquals(List.of("subtotal"), v.violations().stream().map(Violation::path).toList());
            assertTrue(v.violations().get(0).message().contains("not on page 1 (it is on page 2)"), v.violations().toString());
        }
    }

    @Test
    @DisplayName("R6: a page past the end of the document is rejected")
    void pagePastEndRejected() {
        String json = "{\"vendor\": " + ev("\"Acme Supplies Ltd\"", 3, "ACME SUPPLIES LTD")
                + ", \"subtotal\": " + ev("74.50", 2, "Subtotal   74.50") + "}";
        Verdict v = run(groundedVendorAndSubtotal(), json, WITH_SOURCE);
        assertEquals(List.of("vendor"), v.violations().stream().map(Violation::path).toList());
        assertTrue(v.violations().get(0).message().contains("past the end"), v.violations().toString());
    }

    @Test
    @DisplayName("R6: an optional field may be missing or null, but a value must carry grounded evidence")
    void optionalFieldCheckedWhenPresent() {
        ProvenanceGate gate = ProvenanceGate.builder().require("subtotal").optional("vendor").grounded().build();
        String subtotal = "\"subtotal\": " + ev("74.50", 2, "Subtotal   74.50");
        assertTrue(run(gate, "{" + subtotal + "}", WITH_SOURCE).passed());
        assertTrue(run(gate, "{\"vendor\": null, " + subtotal + "}", WITH_SOURCE).passed());
        assertTrue(run(gate, "{\"vendor\": " + ev("\"Acme Supplies Ltd\"", 1, "ACME SUPPLIES LTD") + ", " + subtotal + "}",
                WITH_SOURCE).passed());

        Verdict bare = run(gate, "{\"vendor\": \"Acme Supplies Ltd\", " + subtotal + "}", WITH_SOURCE);
        assertEquals(List.of("vendor"), bare.violations().stream().map(Violation::path).toList());
        Verdict invented = run(gate, "{\"vendor\": " + ev("\"Acme\"", 1, "ACME LTD") + ", " + subtotal + "}", WITH_SOURCE);
        assertEquals(List.of("vendor"), invented.violations().stream().map(Violation::path).toList());
    }

    @Test
    @DisplayName("R6: a gate with no required field, or a bad path, is rejected at build")
    void badConfigRejected() {
        assertThrows(IllegalStateException.class, () -> ProvenanceGate.builder().build());
        assertThrows(IllegalArgumentException.class, () -> ProvenanceGate.builder().require("$.vendor"));
    }

    @Test
    @DisplayName("R6: a source document needs at least one page")
    void emptyDocumentRejected() {
        assertThrows(IllegalArgumentException.class, SourceDocument::ofPages);
    }
}
