package io.github.ravismash.outputgate.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * R11 acceptance: one integration test against a recorded response. A real qwen2.5:14b
 * answer, recorded from local Ollama, is replayed over HTTP to Spring AI's own
 * OllamaChatModel; nothing calls a live model.
 */
class SpringAiRecordedResponseTest {

    @Test
    @DisplayName("R11: Spring AI ChatClient → recorded Ollama response → text, tokens, cost and latency")
    void recordedResponse() throws Exception {
        try (ReplayServer server = new ReplayServer(Recording.RESOURCE)) {
            OllamaChatModel model = OllamaChatModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(server.baseUrl()).build())
                    .defaultOptions(OllamaChatOptions.builder().model("qwen2.5:14b").temperature(0.0).build())
                    .build();
            TokenPricing pricing = TokenPricing.perMillionTokens(new BigDecimal("0.15"), new BigDecimal("0.60"));

            LlmOutput out = new SpringAiLlmClient(ChatClient.create(model), pricing).complete(Recording.prompt());

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
