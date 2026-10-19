package io.github.ravismash.outputgate.langchain4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.model.ollama.OllamaChatModel;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R11 acceptance: one integration test against a recorded response. The same real
 * qwen2.5:14b answer is replayed over HTTP to LangChain4j's own OllamaChatModel.
 */
class LangChain4jRecordedResponseTest {

    @Test
    @DisplayName("R11: LangChain4j ChatModel → recorded Ollama response → text, tokens, cost and latency")
    void recordedResponse() throws Exception {
        try (ReplayServer server = new ReplayServer(Recording.RESOURCE)) {
            OllamaChatModel model = OllamaChatModel.builder()
                    .baseUrl(server.baseUrl())
                    .modelName("qwen2.5:14b")
                    .temperature(0.0)
                    .timeout(Duration.ofSeconds(10))
                    .build();
            TokenPricing pricing = TokenPricing.perMillionTokens(new BigDecimal("0.15"), new BigDecimal("0.60"));

            LlmOutput out = new LangChain4jLlmClient(model, pricing).complete(Recording.prompt());

            assertEquals(Recording.content(), out.text());
            assertEquals(Recording.outputTokens(), out.usage().outputTokens());
            assertEquals(0, pricing.cost(Recording.promptTokens(), Recording.outputTokens()).orElseThrow()
                    .compareTo(out.usage().cost()), () -> out.usage().toString());
            assertTrue(out.usage().latency().toNanos() > 0, () -> out.usage().toString());

            assertEquals(1, server.requests().stream().filter(r -> r.path().equals("/api/chat")).count(), () -> server.requests().toString());
            String sent = server.requests().get(server.requests().size() - 1).body();
            assertTrue(sent.contains("qwen2.5:14b") && sent.contains("Invoice No: INV-2041"), sent);
        }
    }
}
