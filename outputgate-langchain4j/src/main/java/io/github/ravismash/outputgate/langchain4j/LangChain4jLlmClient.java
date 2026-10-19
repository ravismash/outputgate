package io.github.ravismash.outputgate.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.Usage;
import java.time.Duration;

/**
 * An {@link LlmClient} over a LangChain4j {@link ChatModel} (R11; the FRD's
 * {@code ChatLanguageModel}, renamed in LangChain4j 1.x). Fills {@code Usage}: latency
 * measured around the call, output tokens from the response, cost from your
 * {@link TokenPricing}. Missing token counts stay unknown, never zero. Errors from the
 * model are not caught.
 */
public final class LangChain4jLlmClient implements LlmClient {

    private final ChatModel model;
    private final TokenPricing pricing;

    /** @param model the LangChain4j model; cost stays unknown without pricing */
    public LangChain4jLlmClient(ChatModel model) {
        this(model, null);
    }

    /** @param model the LangChain4j model @param pricing what you pay per token, or null for unknown cost */
    public LangChain4jLlmClient(ChatModel model, TokenPricing pricing) {
        if (model == null) {
            throw new IllegalArgumentException("model is null");
        }
        this.model = model;
        this.pricing = pricing;
    }

    @Override
    public LlmOutput complete(String prompt) {
        long start = System.nanoTime();
        ChatResponse response = model.chat(ChatRequest.builder().messages(UserMessage.from(prompt)).build());
        Duration latency = Duration.ofNanos(System.nanoTime() - start);

        AiMessage message = response == null ? null : response.aiMessage();
        String text = message == null ? null : message.text();
        TokenUsage usage = response == null ? null : response.tokenUsage();
        Long in = usage == null || usage.inputTokenCount() == null ? null : usage.inputTokenCount().longValue();
        Long out = usage == null || usage.outputTokenCount() == null ? null : usage.outputTokenCount().longValue();
        var cost = pricing == null ? null : pricing.cost(in, out).orElse(null);
        return new LlmOutput(text == null ? "" : text, new Usage(cost, latency, out));
    }
}
