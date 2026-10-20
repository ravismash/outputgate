package io.github.ravismash.outputgate.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ravismash.outputgate.BudgetGate;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Offline tests against a local fake Ollama server. */
class OllamaLlmClientTest {

    private static final TokenPricing PRICE = TokenPricing.perMillionTokens(new BigDecimal("0.15"), new BigDecimal("0.60"));

    private static OllamaLlmClient client(FakeOllama server) {
        return OllamaLlmClient.builder().baseUrl(server.baseUrl()).model("qwen2.5:14b").pricing(PRICE).build();
    }

    @Test
    @DisplayName("R11: recorded Ollama response → text, output tokens, cost and latency")
    void recordedResponse() throws Exception {
        try (FakeOllama server = FakeOllama.recorded(Recording.RESOURCE)) {
            LlmOutput out = client(server).complete(Recording.prompt());
            assertEquals(Recording.content(), out.text());
            assertEquals(Recording.outputTokens(), out.usage().outputTokens());
            assertEquals(0, PRICE.cost(Recording.promptTokens(), Recording.outputTokens()).orElseThrow()
                    .compareTo(out.usage().cost()), () -> out.usage().toString());
            assertTrue(out.usage().latency().toNanos() > 0);
        }
    }

    @Test
    @DisplayName("R11: the request is a non-streaming POST /api/chat with the model, the prompt and the options")
    void requestShape() throws Exception {
        try (FakeOllama server = FakeOllama.recorded(Recording.RESOURCE)) {
            OllamaLlmClient.builder().baseUrl(server.baseUrl()).model("qwen2.5:14b").temperature(0.0).seed(42).build()
                    .complete("extract this");
            FakeOllama.Request r = server.requests().get(0);
            assertEquals("POST", r.method());
            assertEquals("/api/chat", r.path());
            assertTrue(r.contentType().startsWith("application/json"), r.contentType());
            JsonNode body = new ObjectMapper().readTree(r.body());
            assertEquals("qwen2.5:14b", body.path("model").asText());
            assertEquals(false, body.path("stream").asBoolean(true));
            assertEquals("user", body.path("messages").get(0).path("role").asText());
            assertEquals("extract this", body.path("messages").get(0).path("content").asText());
            assertEquals(0.0, body.path("options").path("temperature").asDouble(-1));
            assertEquals(42, body.path("options").path("seed").asInt(-1));
        }
    }

    @Test
    @DisplayName("R11: contextSize is sent as options.num_ctx, so Ollama doesn't silently cut a long prompt to its default")
    void contextSizeSent() throws Exception {
        try (FakeOllama server = FakeOllama.recorded(Recording.RESOURCE)) {
            OllamaLlmClient.builder().baseUrl(server.baseUrl()).model("m").contextSize(16384).build().complete("p");
            JsonNode body = new ObjectMapper().readTree(server.requests().get(0).body());
            assertEquals(16384, body.path("options").path("num_ctx").asInt(-1));
        }
        assertThrows(IllegalArgumentException.class, () -> OllamaLlmClient.builder().model("m").contextSize(0));
    }

    @Test
    @DisplayName("R11: a base URL with a trailing slash still reaches /api/chat")
    void trailingSlash() throws Exception {
        try (FakeOllama server = FakeOllama.recorded(Recording.RESOURCE)) {
            OllamaLlmClient.builder().baseUrl(server.baseUrl() + "///").model("m").build().complete("p");
            assertEquals("/api/chat", server.requests().get(0).path());
        }
    }

    @Test
    @DisplayName("R11: format is sent as Ollama's format, either \"json\" or a JSON Schema object; none by default")
    void formatSent() throws Exception {
        try (FakeOllama server = FakeOllama.recorded(Recording.RESOURCE)) {
            OllamaLlmClient.builder().baseUrl(server.baseUrl()).model("m").build().complete("p");
            OllamaLlmClient.builder().baseUrl(server.baseUrl()).model("m").format("json").build().complete("p");
            OllamaLlmClient.builder().baseUrl(server.baseUrl()).model("m")
                    .format("{\"type\": \"object\", \"required\": [\"a\"]}").build().complete("p");
            ObjectMapper json = new ObjectMapper();
            assertTrue(json.readTree(server.requests().get(0).body()).path("format").isMissingNode());
            assertEquals("json", json.readTree(server.requests().get(1).body()).path("format").asText());
            JsonNode schema = json.readTree(server.requests().get(2).body()).path("format");
            assertEquals("object", schema.path("type").asText());
            assertEquals("a", schema.path("required").get(0).asText());
        }
        assertThrows(IllegalArgumentException.class, () -> OllamaLlmClient.builder().model("m").format(""));
        assertThrows(IllegalArgumentException.class, () -> OllamaLlmClient.builder().model("m").format("{not json"));
        assertThrows(IllegalArgumentException.class, () -> OllamaLlmClient.builder().model("m").format("[1]"));
    }

    @Test
    @DisplayName("R11: missing token counts are unknown, not zero, so a token ceiling fails closed")
    void missingCountsUnknown() throws Exception {
        try (FakeOllama server = FakeOllama.answering(200, "{\"message\": {\"role\": \"assistant\", \"content\": \"{}\"}, \"done\": true}")) {
            LlmOutput out = client(server).complete("p");
            assertEquals("{}", out.text());
            assertNull(out.usage().outputTokens());
            assertNull(out.usage().cost());
            LlmAssert.assertThat(out).isRejectedBy(Contract.builder().gate(BudgetGate.builder().maxOutputTokens(10).build()).build())
                    .byGate("budget");
        }
    }

    @Test
    @DisplayName("R11: without pricing the cost stays unknown")
    void noPricing() throws Exception {
        try (FakeOllama server = FakeOllama.recorded(Recording.RESOURCE)) {
            LlmOutput out = OllamaLlmClient.builder().baseUrl(server.baseUrl()).model("m").build().complete("p");
            assertEquals(Recording.outputTokens(), out.usage().outputTokens());
            assertNull(out.usage().cost());
        }
    }

    @Test
    @DisplayName("R11: an Ollama error (e.g. unknown model) is thrown with its status and message, never returned as text")
    void errorThrown() throws Exception {
        try (FakeOllama server = FakeOllama.answering(404, "{\"error\": \"model 'nope' not found\"}")) {
            OllamaException e = assertThrows(OllamaException.class, () -> client(server).complete("p"));
            assertEquals(404, e.status());
            assertTrue(e.getMessage().contains("model 'nope' not found"), e.getMessage());
        }
    }

    @Test
    @DisplayName("R11: a response that is not Ollama JSON is an error")
    void badBodyThrown() throws Exception {
        try (FakeOllama server = FakeOllama.answering(200, "<html>proxy error</html>")) {
            assertThrows(OllamaException.class, () -> client(server).complete("p"));
        }
    }

    @Test
    @DisplayName("R11: an answer slower than the timeout is an error")
    void timeout() throws Exception {
        try (FakeOllama server = FakeOllama.slow(Duration.ofSeconds(3), "{}")) {
            OllamaLlmClient c = OllamaLlmClient.builder().baseUrl(server.baseUrl()).model("m").timeout(Duration.ofMillis(300)).build();
            OllamaException e = assertThrows(OllamaException.class, () -> c.complete("p"));
            assertEquals(0, e.status());
        }
    }

    @Test
    @DisplayName("R11: nothing listening is an error with status 0")
    void unreachable() {
        OllamaLlmClient c = OllamaLlmClient.builder().baseUrl("http://127.0.0.1:9").model("m").timeout(Duration.ofSeconds(2)).build();
        OllamaException e = assertThrows(OllamaException.class, () -> c.complete("p"));
        assertEquals(0, e.status());
    }

    @Test
    @DisplayName("R11: a model name is required, and a bad base URL is rejected at build")
    void badConfig() {
        assertThrows(IllegalStateException.class, () -> OllamaLlmClient.builder().build());
        assertThrows(IllegalArgumentException.class, () -> OllamaLlmClient.builder().baseUrl("not a url").model("m").build());
        assertThrows(IllegalArgumentException.class, () -> OllamaLlmClient.builder().model("m").timeout(Duration.ZERO).build());
    }
}
