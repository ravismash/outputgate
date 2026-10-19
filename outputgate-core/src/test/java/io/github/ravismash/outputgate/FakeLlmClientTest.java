package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeLlmClientTest {

    @Test
    @DisplayName("R10: replying returns the same text for every prompt, with unknown usage by default")
    void replying() {
        FakeLlmClient fake = FakeLlmClient.replying("{\"a\": 1}");
        assertEquals(LlmOutput.of("{\"a\": 1}"), fake.complete("one"));
        assertEquals("{\"a\": 1}", fake.complete("two").text());
        assertEquals(List.of("one", "two"), fake.prompts());
    }

    @Test
    @DisplayName("R10: inOrder returns replies in order, and running out is an error")
    void inOrder() {
        FakeLlmClient fake = FakeLlmClient.inOrder("first", "second");
        assertEquals("first", fake.complete("p").text());
        assertEquals("second", fake.complete("p").text());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> fake.complete("p"));
        assertTrue(e.getMessage().contains("2"), e.getMessage());
    }

    @Test
    @DisplayName("R10: byPrompt answers known prompts and refuses unknown ones")
    void byPrompt() {
        FakeLlmClient fake = FakeLlmClient.byPrompt(Map.of("extract A", "{\"a\": 1}"));
        assertEquals("{\"a\": 1}", fake.complete("extract A").text());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> fake.complete("extract B"));
        assertTrue(e.getMessage().contains("extract B"), e.getMessage());
    }

    @Test
    @DisplayName("R10: withUsage reports that usage on every reply, so BudgetGate can be tested offline")
    void withUsage() {
        Usage usage = Usage.of(new BigDecimal("0.004"), Duration.ofMillis(800), 150);
        FakeLlmClient fake = FakeLlmClient.replying("{\"ok\": true}").withUsage(usage);
        LlmOutput out = fake.complete("p");
        assertEquals(usage, out.usage());
        Verdict v = Contract.builder().gate(BudgetGate.builder().maxCost(0.01).build()).build().evaluate(out);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @Test
    @DisplayName("R10: LlmClient is one method, so a lambda is a client")
    void lambdaClient() {
        LlmClient echo = prompt -> LlmOutput.of("{\"echo\": \"" + prompt + "\"}");
        assertEquals("{\"echo\": \"hi\"}", echo.complete("hi").text());
    }

    @Test
    @DisplayName("R10: a fake needs at least one reply")
    void needsReplies() {
        assertThrows(IllegalArgumentException.class, FakeLlmClient::inOrder);
        assertThrows(IllegalArgumentException.class, () -> FakeLlmClient.byPrompt(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> FakeLlmClient.replying(null));
    }
}
