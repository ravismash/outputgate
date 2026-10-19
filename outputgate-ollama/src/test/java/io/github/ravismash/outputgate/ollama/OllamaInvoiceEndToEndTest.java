package io.github.ravismash.outputgate.ollama;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.example.InvoiceExample;
import io.github.ravismash.outputgate.example.InvoiceExtractor;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** The invoice example through the Ollama adapter: offline on the recording, and live when asked. */
class OllamaInvoiceEndToEndTest {

    @Test
    @DisplayName("offline: the recorded qwen2.5:14b answer, through the Ollama adapter, passes the invoice contract")
    void recordedAnswerPasses() throws Exception {
        try (FakeOllama server = FakeOllama.recorded(Recording.RESOURCE)) {
            OllamaLlmClient client = OllamaLlmClient.builder().baseUrl(server.baseUrl()).model("qwen2.5:14b").pricing(TokenPricing.FREE).build();
            LlmOutput out = new InvoiceExtractor(client).extract(InvoiceExample.document());
            LlmAssert.assertThat(out).withContext(InvoiceExample.context()).passes(InvoiceExample.contract());
        }
    }

    /**
     * Day 9: run the invoice example locally against a real Ollama. Not part of the normal
     * build (it needs Ollama and takes about a minute); run with OUTPUTGATE_LIVE_OLLAMA=1.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "OUTPUTGATE_LIVE_OLLAMA", matches = "1")
    @DisplayName("live: the invoice example against local Ollama qwen2.5:14b")
    void liveLocalOllama() {
        OllamaLlmClient client = OllamaLlmClient.builder()
                .model(System.getenv().getOrDefault("OUTPUTGATE_OLLAMA_MODEL", "qwen2.5:14b"))
                .temperature(0.0)
                .seed(42)
                .timeout(Duration.ofMinutes(5))
                .pricing(TokenPricing.FREE)
                .build();
        LlmOutput out = new InvoiceExtractor(client).extract(InvoiceExample.document());
        Verdict v = InvoiceExample.contract().evaluate(out, InvoiceExample.context());
        System.out.println("LIVE usage: " + out.usage());
        System.out.println("LIVE verdict: " + v.status());
        v.results().forEach(r -> System.out.println("LIVE   " + r.gate() + ": " + (r.passed() ? "pass" : "FAIL")));
        v.violations().forEach(x -> System.out.println("LIVE     [" + x.gate() + "] " + x.path() + ": " + x.message()));
        System.out.println("LIVE text: " + out.text());
        assertTrue(out.usage().outputTokens() != null && out.usage().latency() != null, "usage filled");
        LlmAssert.assertThat(out).withContext(InvoiceExample.context()).passes(InvoiceExample.contract());
    }
}
