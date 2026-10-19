package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetGateTest {

    private static final String CORRECT = "{\"subtotal\": 100.00}";

    private static Contract contract(BudgetGate gate) {
        return Contract.builder().gate(gate).build();
    }

    private static Usage usage(String cost, long millis, long tokens) {
        return Usage.of(new BigDecimal(cost), Duration.ofMillis(millis), tokens);
    }

    @Test
    @DisplayName("R7: a correct output over the cost ceiling is REJECTED")
    void overCostRejected() {
        Verdict v = contract(BudgetGate.builder().maxCost(new BigDecimal("0.010")).build())
                .evaluate(new LlmOutput(CORRECT, usage("0.012", 800, 120)));
        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        Violation violation = v.violations().get(0);
        assertEquals("budget", violation.gate());
        assertTrue(violation.message().contains("0.012") && violation.message().contains("0.010"), violation.message());
    }

    @Test
    @DisplayName("R7: Usage.UNKNOWN with a ceiling set is REJECTED")
    void unknownUsageWithCeilingRejected() {
        Verdict v = contract(BudgetGate.builder().maxCost(0.01).build()).evaluate(LlmOutput.of(CORRECT));
        assertEquals(Status.REJECTED, v.status());
        assertTrue(v.violations().get(0).message().contains("unknown"), v.violations().toString());
    }

    @Test
    @DisplayName("R7: raw text with no usage counts as unknown usage")
    void plainStringIsUnknownUsage() {
        Verdict v = contract(BudgetGate.builder().maxOutputTokens(500).build()).evaluate(CORRECT);
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        assertTrue(v.violations().get(0).message().startsWith("output tokens unknown"), v.violations().toString());
    }

    @Test
    @DisplayName("R7: usage within every ceiling passes, and a ceiling is inclusive")
    void withinCeilingsPasses() {
        BudgetGate gate = BudgetGate.builder()
                .maxCost(new BigDecimal("0.012"))
                .maxLatency(Duration.ofSeconds(2))
                .maxOutputTokens(120)
                .build();
        Verdict v = contract(gate).evaluate(new LlmOutput(CORRECT, usage("0.012", 2000, 120)));
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("R7: latency and output-token ceilings each reject when exceeded, and all are reported")
    void everyCeilingReported() {
        BudgetGate gate = BudgetGate.builder()
                .maxCost(1.0)
                .maxLatency(Duration.ofSeconds(2))
                .maxOutputTokens(100)
                .build();
        Verdict v = contract(gate).evaluate(new LlmOutput(CORRECT, usage("0.5", 2500, 101)));
        List<String> messages = v.violations().stream().map(Violation::message).toList();
        assertEquals(2, messages.size(), messages::toString);
        assertTrue(messages.get(0).startsWith("latency") && messages.get(1).startsWith("output tokens"), messages::toString);
    }

    @Test
    @DisplayName("R7: a part of usage that is unknown fails only the ceiling that needs it")
    void partlyUnknownUsage() {
        BudgetGate gate = BudgetGate.builder().maxCost(1.0).maxOutputTokens(100).build();
        Verdict v = contract(gate).evaluate(new LlmOutput(CORRECT, new Usage(new BigDecimal("0.5"), null, null)));
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        assertTrue(v.violations().get(0).message().startsWith("output tokens unknown"), v.violations().toString());
    }

    @Test
    @DisplayName("R7: the output's usage is used even when a context is passed too")
    void outputUsageWinsOverContext() {
        EvalContext context = EvalContext.empty().withUsage(usage("9.99", 1, 1));
        Verdict v = contract(BudgetGate.builder().maxCost(1.0).build())
                .evaluate(new LlmOutput(CORRECT, usage("0.5", 1, 1)), context);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("R7: a budget gate with no ceiling, or a negative ceiling, is rejected at build")
    void badConfigRejected() {
        assertThrows(IllegalStateException.class, () -> BudgetGate.builder().build());
        assertThrows(IllegalArgumentException.class, () -> BudgetGate.builder().maxCost(-1));
        assertThrows(IllegalArgumentException.class, () -> BudgetGate.builder().maxOutputTokens(-1));
        assertThrows(IllegalArgumentException.class, () -> BudgetGate.builder().maxLatency(Duration.ofMillis(-1)));
    }

    @Test
    @DisplayName("R7: usage values cannot be negative")
    void negativeUsageRejected() {
        assertThrows(IllegalArgumentException.class, () -> usage("-0.01", 1, 1));
    }
}
