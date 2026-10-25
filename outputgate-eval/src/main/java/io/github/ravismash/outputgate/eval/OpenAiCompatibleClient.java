package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.Usage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * A model behind an OpenAI-compatible {@code chat/completions} endpoint, such as LM Studio's local
 * server or Gemini's OpenAI-compatible API, for the eval only (the library's adapters are Spring AI,
 * LangChain4j and Ollama). Sends one user message at temperature 0 and, when given, the answer's
 * JSON Schema as {@code response_format}. A rate-limited call (HTTP 429 or 503) is retried with backoff.
 */
final class OpenAiCompatibleClient implements LlmClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI endpoint;
    private final String model;
    private final JsonNode schema;
    private final Duration timeout;
    private final String apiKey;
    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * @param baseUrl the part before {@code /chat/completions}, e.g. {@code http://localhost:1234/v1}
     * @param model the server's model id
     * @param schema the answer's JSON Schema as text, or null for free text
     * @param timeout how long to wait for one answer
     * @param apiKey sent as a bearer token, or null for none
     */
    OpenAiCompatibleClient(String baseUrl, String model, String schema, Duration timeout, String apiKey) {
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        this.apiKey = apiKey;
        this.model = model;
        try {
            this.schema = schema == null ? null : JSON.readTree(schema);
        } catch (IOException e) {
            throw new IllegalArgumentException("schema is not JSON", e);
        }
        this.timeout = timeout;
    }

    @Override
    public LlmOutput complete(String prompt) {
        ObjectNode body = JSON.createObjectNode().put("model", model).put("temperature", 0.0).put("seed", 42);
        body.putArray("messages").addObject().put("role", "user").put("content", prompt);
        if (schema != null) {
            ObjectNode format = body.putObject("response_format").put("type", "json_schema");
            format.putObject("json_schema").put("name", "extraction").set("schema", schema);
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (apiKey != null && !apiKey.isBlank()) {
            req.header("Authorization", "Bearer " + apiKey);
        }
        long start = 0;
        HttpResponse<String> res;
        try {
            for (int attempt = 0; ; attempt++) {
                start = System.nanoTime();
                res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
                if ((res.statusCode() != 429 && res.statusCode() != 503) || attempt == 6) {
                    break;
                }
                Thread.sleep(Duration.ofSeconds(10L << Math.min(attempt, 4)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
        Duration latency = Duration.ofNanos(System.nanoTime() - start);
        if (res.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " from " + endpoint + ": " + res.body());
        }
        JsonNode r;
        try {
            r = JSON.readTree(res.body());
        } catch (IOException e) {
            throw new IllegalStateException("not JSON from " + endpoint, e);
        }
        String text = r.path("choices").path(0).path("message").path("content").asText(null);
        if (text == null) {
            throw new IllegalStateException("no message content from " + endpoint + ": " + res.body());
        }
        JsonNode tokens = r.path("usage").path("completion_tokens");
        return new LlmOutput(text, new Usage(null, latency, tokens.canConvertToLong() ? tokens.asLong() : null));
    }
}
