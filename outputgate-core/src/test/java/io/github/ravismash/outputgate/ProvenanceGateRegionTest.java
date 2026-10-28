package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Quotes scoped to a region: one of two tables that print the same labels. */
class ProvenanceGateRegionTest {

    /** A parent's figures, then a subsidiary's, with the same labels and nearly the same numbers. */
    private static final SourceDocument REPORT = SourceDocument.ofPages("""
            Quarterly results - Northwind Group
            Revenue   1,200
            Costs   900
            Profit   300
            Northwind Retail (subsidiary)
            Revenue   1,200
            Costs   905
            Profit   295
            """);

    private static final EvalContext CTX = EvalContext.empty().withSource(REPORT);

    /** The group's table: from the top of the document up to the subsidiary's heading. */
    private static final Region GROUP = Region.fromDocumentStart().before("\\(subsidiary\\)");

    private static String ev(String value, int page, String text) {
        return "{\"value\": " + value + ", \"source_span\": {\"page\": " + page + ", \"text\": \"" + text + "\"}}";
    }

    private static String answer(String costs, String profit) {
        return "{\"revenue\": " + ev("1200", 1, "1,200") + ", \"costs\": " + ev(costs, 1, "Costs   " + costs)
                + ", \"profit\": " + ev(profit, 1, profit) + "}";
    }

    private static ProvenanceGate.Builder gate() {
        return ProvenanceGate.builder().require("revenue", "costs", "profit").grounded();
    }

    private static Verdict run(ProvenanceGate gate, String json, EvalContext context) {
        return Contract.builder().gate(gate).build().evaluate(json, context);
    }

    @Test
    @DisplayName("quotes from the right table pass")
    void quotesInsideTheRegionPass() {
        Verdict v = run(gate().within(GROUP, "revenue", "costs", "profit").build(), answer("900", "300"), CTX);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("a quote that is on the page but from the other table is rejected; without a region it passes")
    void quoteFromTheOtherTableRejected() {
        String mixed = answer("905", "295");
        assertTrue(run(gate().build(), mixed, CTX).passed(), "grounding alone accepts it: both quotes are on the page");

        Verdict v = run(gate().within(GROUP, "revenue", "costs", "profit").build(), mixed, CTX);
        assertEquals(Status.REJECTED, v.status());
        assertEquals(2, v.violations().size(), () -> v.violations().toString());
        assertEquals("costs", v.violations().get(0).path());
        assertEquals("profit", v.violations().get(1).path());
        String message = v.violations().get(0).message();
        assertTrue(message.contains("\"Costs   905\" is on page 1 but not inside"), message);
        assertTrue(message.contains("page 1, lines 1 to 4"), message);
    }

    @Test
    @DisplayName("only the fields named are scoped")
    void onlyNamedFieldsScoped() {
        Verdict v = run(gate().within(GROUP, "costs").build(), answer("900", "295"), CTX);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("from(...) starts at a heading, and through(...) keeps the end line while before(...) drops it")
    void startAndEndAnchors() {
        Region subsidiary = Region.from("\\(subsidiary\\)");
        assertTrue(run(gate().within(subsidiary, "costs", "profit").build(), answer("905", "295"), CTX).passed());
        assertEquals(Status.REJECTED, run(gate().within(subsidiary, "costs").build(), answer("900", "295"), CTX).status());

        String profit300 = answer("900", "300");
        assertTrue(run(gate().within(Region.fromDocumentStart().through("^Profit"), "profit").build(), profit300, CTX).passed());
        Verdict before = run(gate().within(Region.fromDocumentStart().before("^Profit"), "profit").build(), profit300, CTX);
        assertEquals(Status.REJECTED, before.status(), () -> before.violations().toString());
    }

    @Test
    @DisplayName("a region whose start is not in the document is a violation, never a pass")
    void missingStartFailsClosed() {
        Verdict v = run(gate().within(Region.from("Balance sheet"), "costs").build(), answer("900", "300"), CTX);
        assertEquals(Status.REJECTED, v.status());
        assertEquals("costs", v.violations().get(0).path());
        assertTrue(v.violations().get(0).message().contains("not in the source document"), v.violations().get(0).message());
    }

    @Test
    @DisplayName("an end that no line matches runs the region to the end of the document")
    void missingEndRunsToTheEnd() {
        Region open = Region.fromDocumentStart().through("Grand total");
        assertTrue(run(gate().within(open, "costs", "profit").build(), answer("905", "295"), CTX).passed());
    }

    @Test
    @DisplayName("a region runs across pages, and a quote on a page outside it is rejected")
    void regionAcrossPages() {
        SourceDocument doc = SourceDocument.ofPages("Cover\nTable A\nRow   10", "Row   20\nEnd of table A\nTable B\nRow   30");
        Region tableA = Region.from("^Table A").through("^End of table A");
        ProvenanceGate g = ProvenanceGate.builder().require("rows[*]").grounded().within(tableA, "rows[*]").build();
        EvalContext ctx = EvalContext.empty().withSource(doc);
        String inside = "{\"rows\": [" + ev("10", 1, "Row   10") + ", " + ev("20", 2, "Row   20") + "]}";
        assertTrue(run(g, inside, ctx).passed(), () -> run(g, inside, ctx).violations().toString());
        String outside = "{\"rows\": [" + ev("10", 1, "Row   10") + ", " + ev("30", 2, "Row   30") + "]}";
        Verdict v = run(g, outside, ctx);
        assertEquals(Status.REJECTED, v.status());
        assertEquals("rows[1]", v.violations().get(0).path());
        assertTrue(v.violations().get(0).message().contains("page 1 line 2 to page 2 line 2"), v.violations().get(0).message());
    }

    @Test
    @DisplayName("ignoring whitespace and currency signs applies inside the region too")
    void looseningAppliesInsideTheRegion() {
        ProvenanceGate g = ProvenanceGate.builder().require("costs").groundedIgnoringWhitespace().ignoringCurrencySigns()
                .within(GROUP, "costs").build();
        assertTrue(run(g, "{\"costs\": " + ev("900", 1, "Costs $900") + "}", CTX).passed());
        assertEquals(Status.REJECTED, run(g, "{\"costs\": " + ev("905", 1, "Costs $905") + "}", CTX).status());
    }

    @Test
    @DisplayName("an optional field that is missing is not checked against its region")
    void missingOptionalFieldSkipped() {
        ProvenanceGate g = ProvenanceGate.builder().require("revenue").optional("note").grounded()
                .within(GROUP, "revenue", "note").build();
        assertTrue(run(g, "{\"revenue\": " + ev("1200", 1, "1,200") + ", \"note\": null}", CTX).passed());
    }

    @Test
    @DisplayName("a region needs grounding, and may only scope fields the gate checks")
    void misconfigurationIsAnError() {
        assertThrows(IllegalStateException.class,
                () -> ProvenanceGate.builder().require("costs").within(GROUP, "costs").build());
        assertThrows(IllegalStateException.class,
                () -> ProvenanceGate.builder().require("costs").grounded().within(GROUP, "profit").build());
        assertThrows(IllegalArgumentException.class, () -> ProvenanceGate.builder().within(GROUP));
    }

    @Test
    @DisplayName("a region describes itself for a retry prompt")
    void describesItself() {
        assertEquals("from the start of the document up to the next line matching /\\(subsidiary\\)/", GROUP.toString());
        assertEquals("from the first line matching /^Table/ through the next line matching /Total/",
                Region.from("^Table").through("Total").toString());
    }
}
