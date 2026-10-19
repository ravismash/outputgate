package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmOutputTest {

    @Test
    @DisplayName("LlmOutput.of has unknown usage; a null usage is read as unknown")
    void unknownByDefault() {
        assertEquals(Usage.UNKNOWN, LlmOutput.of("{}").usage());
        assertEquals(Usage.UNKNOWN, new LlmOutput("{}", null).usage());
    }

    @Test
    @DisplayName("Contract.evaluate(LlmOutput) gives gates the same verdict as raw text for the same text")
    void sameVerdictAsText() {
        Contract c = Contract.builder()
                .gate(MathGate.builder().sumEquals("items[*]", "total").build())
                .build();
        String text = "{\"items\": [1, 2], \"total\": 4}";
        Usage usage = Usage.of(new BigDecimal("0.01"), Duration.ofMillis(10), 5);
        assertEquals(c.evaluate(text), c.evaluate(new LlmOutput(text, usage)));
    }

    @Test
    @DisplayName("R2 still applies: non-JSON text in an LlmOutput is rejected as json-parse")
    void nonJsonRejected() {
        Verdict v = Contract.builder()
                .gate(BudgetGate.builder().maxCost(1.0).build())
                .build()
                .evaluate(new LlmOutput("Sure! here you go", Usage.of(BigDecimal.ONE, Duration.ZERO, 1)));
        assertEquals("json-parse", v.results().get(0).gate());
        assertTrue(!v.passed());
    }
}
