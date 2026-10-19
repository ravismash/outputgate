package io.github.ravismash.outputgate.langchain4j;

import dev.langchain4j.model.ollama.OllamaChatModel;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.example.InvoiceExample;
import io.github.ravismash.outputgate.example.InvoiceExtractor;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End to end, offline: the example's own extractor asks LangChain4j's OllamaChatModel,
 * which gets the recorded real qwen2.5:14b answer, and the full invoice contract
 * (schema, type, math, grounded provenance, regex) judges it.
 */
class RealAnswerEndToEndTest {

    @Test
    @DisplayName("the recorded qwen2.5:14b invoice answer passes every gate of the invoice contract")
    void realAnswerPasses() throws Exception {
        try (ReplayServer server = new ReplayServer(Recording.RESOURCE)) {
            OllamaChatModel model = OllamaChatModel.builder()
                    .baseUrl(server.baseUrl()).modelName("qwen2.5:14b").timeout(Duration.ofSeconds(10)).build();
            LlmOutput out = new InvoiceExtractor(new LangChain4jLlmClient(model, TokenPricing.FREE))
                    .extract(InvoiceExample.document());
            LlmAssert.assertThat(out).withContext(InvoiceExample.context()).passes(InvoiceExample.contract());
        }
    }
}
