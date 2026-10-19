package io.github.ravismash.outputgate.langchain4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.github.ravismash.outputgate.BudgetGate;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests against a stub LangChain4j ChatModel: no HTTP, no model. */
class LangChain4jLlmClientTest {

    private static final TokenPricing PRICE = TokenPricing.perMillionTokens(new BigDecimal("0.15"), new BigDecimal("0.60"));

    private static ChatModel stub(String text, TokenUsage usage, AtomicReference<ChatRequest> seen) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (seen != null) {
                    seen.set(request);
                }
                return ChatResponse.builder().aiMessage(text == null ? AiMessage.from("") : AiMessage.from(text)).tokenUsage(usage).build();
            }
        };
    }

    @Test
    @DisplayName("R11: text, output tokens, cost and latency are filled from the LangChain4j response")
    void fillsUsage() {
        AtomicReference<ChatRequest> seen = new AtomicReference<>();
        LlmOutput out = new LangChain4jLlmClient(stub("{\"ok\": true}", new TokenUsage(342, 529), seen), PRICE).complete("extract this");
        assertEquals("{\"ok\": true}", out.text());
        assertEquals(529L, out.usage().outputTokens());
        assertEquals(0, new BigDecimal("0.0003687").compareTo(out.usage().cost()), () -> out.usage().toString());
        assertTrue(out.usage().latency() != null && !out.usage().latency().isNegative());
        assertEquals(1, seen.get().messages().size());
        assertEquals("extract this", ((UserMessage) seen.get().messages().get(0)).singleText());
    }

    @Test
    @DisplayName("R11: no token usage from the provider is unknown, not zero")
    void noUsageIsUnknown() {
        LlmOutput out = new LangChain4jLlmClient(stub("{}", null, null), PRICE).complete("p");
        assertNull(out.usage().outputTokens());
        assertNull(out.usage().cost());
        LlmAssert.assertThat(out).isRejectedBy(Contract.builder().gate(BudgetGate.builder().maxOutputTokens(100).build()).build())
                .byGate("budget");
    }

    @Test
    @DisplayName("R11: a partial token usage (output only) still gives no cost")
    void partialUsage() {
        LlmOutput out = new LangChain4jLlmClient(stub("{}", new TokenUsage(null, 7), null), PRICE).complete("p");
        assertEquals(7L, out.usage().outputTokens());
        assertNull(out.usage().cost());
    }

    @Test
    @DisplayName("R11: without pricing the cost stays unknown even when tokens are known")
    void noPricingNoCost() {
        LlmOutput out = new LangChain4jLlmClient(stub("{}", new TokenUsage(1, 2), null)).complete("p");
        assertEquals(2L, out.usage().outputTokens());
        assertNull(out.usage().cost());
    }

    @Test
    @DisplayName("R11: a model error is not swallowed")
    void errorsPropagate() {
        IllegalStateException boom = new IllegalStateException("rate limited");
        ChatModel failing = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw boom;
            }
        };
        RuntimeException e = assertThrows(RuntimeException.class, () -> new LangChain4jLlmClient(failing, PRICE).complete("p"));
        assertTrue(e == boom || e.getCause() == boom, e.toString());
    }

    @Test
    @DisplayName("R11: a null model is rejected at construction")
    void nullModelRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LangChain4jLlmClient(null));
    }
}
