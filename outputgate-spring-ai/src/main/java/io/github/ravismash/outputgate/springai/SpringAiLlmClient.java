package io.github.ravismash.outputgate.springai;

import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.Usage;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * An {@link LlmClient} over a Spring AI {@link ChatClient} (R11). Fills {@code Usage}:
 * latency measured around the call, output tokens from the response, cost from your
 * {@link TokenPricing}. Missing token counts stay unknown, never zero. Errors from the
 * model are not caught.
 */
public final class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;
    private final TokenPricing pricing;

    /** @param chatClient the Spring AI client; cost stays unknown without pricing */
    public SpringAiLlmClient(ChatClient chatClient) {
        this(chatClient, null);
    }

    /** @param chatClient the Spring AI client @param pricing what you pay per token, or null for unknown cost */
    public SpringAiLlmClient(ChatClient chatClient, TokenPricing pricing) {
        if (chatClient == null) {
            throw new IllegalArgumentException("chatClient is null");
        }
        this.chatClient = chatClient;
        this.pricing = pricing;
    }

    @Override
    public LlmOutput complete(String prompt) {
        long start = System.nanoTime();
        ChatResponse response = chatClient.prompt().user(prompt).call().chatResponse();
        Duration latency = Duration.ofNanos(System.nanoTime() - start);

        Generation result = response == null ? null : response.getResult();
        String text = result == null || result.getOutput() == null ? null : result.getOutput().getText();

        Long in = null;
        Long out = null;
        var usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage != null && !(usage instanceof EmptyUsage)) {
            in = usage.getPromptTokens() == null ? null : usage.getPromptTokens().longValue();
            out = usage.getCompletionTokens() == null ? null : usage.getCompletionTokens().longValue();
        }
        var cost = pricing == null ? null : pricing.cost(in, out).orElse(null);
        return new LlmOutput(text == null ? "" : text, new Usage(cost, latency, out));
    }
}
