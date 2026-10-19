package io.github.ravismash.outputgate.junit;

import static io.github.ravismash.outputgate.junit.Contracts.INVOICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.BudgetGate;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Status;
import io.github.ravismash.outputgate.Usage;
import io.github.ravismash.outputgate.Verdict;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmAssertTest {

    private static final String GOOD =
            "{\"subtotal\": 74.50, \"line_items\": [{\"description\": \"Widgets\", \"amount\": 40.00}, {\"description\": \"Bolts\", \"amount\": 34.50}]}";
    private static final String DROPPED =
            "{\"subtotal\": 100.00, \"line_items\": [{\"description\": \"I'm sorry\", \"amount\": 40.00}, {\"description\": \"Bolts\", \"amount\": 34.50}]}";

    @Test
    @DisplayName("passes: a passing output returns its PASSED verdict")
    void passesReturnsVerdict() {
        Verdict v = LlmAssert.assertThat(GOOD).passes(INVOICE);
        assertEquals(Status.PASSED, v.status());
    }

    @Test
    @DisplayName("passes: a rejected output fails the test and lists every violation")
    void passesFailureListsEverything() {
        AssertionError e = assertThrows(AssertionError.class, () -> LlmAssert.assertThat(DROPPED).passes(INVOICE));
        String m = e.getMessage();
        assertTrue(m.contains("REJECTED"), m);
        assertTrue(m.contains("math") && m.contains("subtotal") && m.contains("74.50"), m);
        assertTrue(m.contains("regex") && m.contains("line_items[0].description"), m);
    }

    @Test
    @DisplayName("isRejectedBy(..).byGate(..): passes when that gate rejected the output")
    void rejectedByGate() {
        Verdict v = LlmAssert.assertThat(DROPPED).isRejectedBy(INVOICE).byGate("math").byGate("regex").verdict();
        assertEquals(Status.REJECTED, v.status());
    }

    @Test
    @DisplayName("isRejectedBy: a passing output fails the test")
    void notRejectedFails() {
        AssertionError e = assertThrows(AssertionError.class, () -> LlmAssert.assertThat(GOOD).isRejectedBy(INVOICE));
        assertTrue(e.getMessage().contains("PASSED"), e.getMessage());
    }

    @Test
    @DisplayName("byGate: rejection by a different gate fails the test and names the gates that did fail")
    void wrongGateFails() {
        String onlyMath = "{\"subtotal\": 100.00, \"line_items\": [{\"description\": \"Bolts\", \"amount\": 34.50}]}";
        AssertionError e = assertThrows(AssertionError.class,
                () -> LlmAssert.assertThat(onlyMath).isRejectedBy(INVOICE).byGate("regex"));
        assertTrue(e.getMessage().contains("regex") && e.getMessage().contains("[math]"), e.getMessage());
    }

    @Test
    @DisplayName("byGate(\"json-parse\") covers output that is not JSON")
    void jsonParseGate() {
        LlmAssert.assertThat("Sure! {\"subtotal\": 1}").isRejectedBy(INVOICE).byGate("json-parse");
    }

    @Test
    @DisplayName("LlmOutput usage reaches BudgetGate through the assertion")
    void usageFlows() {
        Contract budget = Contract.builder().gate(BudgetGate.builder().maxCost(0.01).build()).build();
        LlmOutput cheap = new LlmOutput(GOOD, Usage.of(new BigDecimal("0.005"), Duration.ofMillis(10), 10));
        LlmAssert.assertThat(cheap).passes(budget);
        LlmAssert.assertThat(LlmOutput.of(GOOD)).isRejectedBy(budget).byGate("budget");
    }

    @Test
    @DisplayName("withContext passes a source document through for grounding")
    void contextFlows() {
        Contract grounded = Contract.builder()
                .gate(ProvenanceGate.builder().require("vendor").grounded().build())
                .build();
        String out = "{\"vendor\": {\"value\": \"Acme\", \"source_span\": {\"page\": 1, \"text\": \"ACME LTD\"}}}";
        LlmAssert.assertThat(out).withContext(EvalContext.empty().withSource(SourceDocument.of("ACME LTD, Pune")))
                .passes(grounded);
        LlmAssert.assertThat(out).isRejectedBy(grounded).byGate("provenance");
    }
}
