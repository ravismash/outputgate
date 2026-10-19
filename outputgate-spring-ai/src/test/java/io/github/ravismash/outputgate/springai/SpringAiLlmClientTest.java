package io.github.ravismash.outputgate.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.BudgetGate;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** Unit tests against a stub Spring AI ChatModel: no HTTP, no model. */
class SpringAiLlmClientTest {

    private static final TokenPricing PRICE = TokenPricing.perMillionTokens(new BigDecimal("0.15"), new BigDecimal("0.60"));

    private static ChatModel stub(String text, Usage usage, AtomicReference<String> seen) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                if (seen != null) {
                    seen.set(prompt.getContents());
                }
                ChatResponseMetadata md = ChatResponseMetadata.builder().usage(usage).build();
                return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), md);
            }
        };
    }

    @Test
    @DisplayName("R11: text, output tokens, cost and latency are filled from the Spring AI response")
    void fillsUsage() {
        AtomicReference<String> seen = new AtomicReference<>();
        ChatClient chat = ChatClient.create(stub("{\"ok\": true}", new DefaultUsage(342, 529), seen));
        LlmOutput out = new SpringAiLlmClient(chat, PRICE).complete("extract this");
        assertEquals("{\"ok\": true}", out.text());
        assertEquals(529L, out.usage().outputTokens());
        assertEquals(0, new BigDecimal("0.0003687").compareTo(out.usage().cost()), () -> out.usage().toString());
        assertTrue(out.usage().latency() != null && !out.usage().latency().isNegative());
        assertTrue(seen.get().contains("extract this"), seen.get());
    }

    @Test
    @DisplayName("R11: Spring AI's EmptyUsage (provider gave no counts) is unknown, not zero")
    void emptyUsageIsUnknown() {
        ChatClient chat = ChatClient.create(stub("{}", new EmptyUsage(), null));
        LlmOutput out = new SpringAiLlmClient(chat, PRICE).complete("p");
        assertNull(out.usage().outputTokens());
        assertNull(out.usage().cost());
        Contract tokens = Contract.builder().gate(BudgetGate.builder().maxOutputTokens(1000).build()).build();
        LlmAssert.assertThat(out).isRejectedBy(tokens).byGate("budget");
    }

    @Test
    @DisplayName("R11: without pricing the cost stays unknown even when tokens are known")
    void noPricingNoCost() {
        LlmOutput out = new SpringAiLlmClient(ChatClient.create(stub("{}", new DefaultUsage(1, 2), null))).complete("p");
        assertEquals(2L, out.usage().outputTokens());
        assertNull(out.usage().cost());
    }

    @Test
    @DisplayName("R11: a model error is not swallowed")
    void errorsPropagate() {
        IllegalStateException boom = new IllegalStateException("rate limited");
        ChatModel failing = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw boom;
            }
        };
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> new SpringAiLlmClient(ChatClient.create(failing), PRICE).complete("p"));
        assertTrue(e == boom || e.getCause() == boom, e.toString());
    }

    @Test
    @DisplayName("R11: a response with no text becomes empty text, which the contract rejects as json-parse")
    void noTextIsEmpty() {
        LlmOutput out = new SpringAiLlmClient(ChatClient.create(stub(null, new DefaultUsage(1, 0), null)), PRICE).complete("p");
        assertEquals("", out.text());
        LlmAssert.assertThat(out).isRejectedBy(Contract.builder().gate(BudgetGate.builder().maxCost(1).build()).build())
                .byGate("json-parse");
    }

    @Test
    @DisplayName("R11: a null client is rejected at construction")
    void nullClientRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SpringAiLlmClient(null));
        assertSame(TokenPricing.FREE, TokenPricing.FREE);
    }
}
