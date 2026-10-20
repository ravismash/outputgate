package io.github.ravismash.outputgate.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.Usage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * An {@link LlmClient} over Ollama's HTTP chat API ({@code POST /api/chat}, non-streaming) (R11).
 * Fills {@code Usage}: latency measured around the call, output tokens from {@code eval_count},
 * cost from your {@link TokenPricing} using {@code prompt_eval_count} and {@code eval_count}.
 * An Ollama error, an unreachable server or a timeout throws {@link OllamaException}; it is
 * never returned as model text.
 */
public final class OllamaLlmClient implements LlmClient {

    /** Ollama's default address. */
    public static final URI DEFAULT_BASE_URL = URI.create("http://localhost:11434");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI chatUrl;
    private final String model;
    private final Double temperature;
    private final Integer seed;
    private final Integer contextSize;
    private final JsonNode format;
    private final Duration timeout;
    private final TokenPricing pricing;
    private final HttpClient http;

    private OllamaLlmClient(Builder b) {
        this.chatUrl = URI.create(withoutTrailingSlashes(b.baseUrl.toString()) + "/api/chat");
        this.model = b.model;
        this.temperature = b.temperature;
        this.seed = b.seed;
        this.contextSize = b.contextSize;
        this.format = b.format;
        this.timeout = b.timeout;
        this.pricing = b.pricing;
        Duration connect = timeout.compareTo(Duration.ofSeconds(10)) < 0 ? timeout : Duration.ofSeconds(10);
        this.http = HttpClient.newBuilder().connectTimeout(connect).build();
    }

    /** @return a builder; {@code model} is required */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public LlmOutput complete(String prompt) {
        HttpRequest request = HttpRequest.newBuilder(chatUrl)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(prompt), StandardCharsets.UTF_8))
                .build();
        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OllamaException(0, "interrupted while waiting for Ollama at " + chatUrl, e);
        } catch (IOException e) {
            throw new OllamaException(0, "could not get an answer from Ollama at " + chatUrl + ": " + e, e);
        }
        Duration latency = Duration.ofNanos(System.nanoTime() - start);

        JsonNode json;
        try {
            json = MAPPER.readTree(response.body());
        } catch (JsonProcessingException e) {
            json = null;
        }
        if (response.statusCode() != 200) {
            String error = json != null && json.hasNonNull("error") ? json.get("error").asText() : snippet(response.body());
            throw new OllamaException(response.statusCode(), "Ollama returned " + response.statusCode() + ": " + error, null);
        }
        if (json == null || !json.isObject()) {
            throw new OllamaException(200, "Ollama's answer is not Ollama JSON: " + snippet(response.body()), null);
        }
        String text = json.path("message").path("content").asText("");
        Long in = count(json, "prompt_eval_count");
        Long out = count(json, "eval_count");
        var cost = pricing == null ? null : pricing.cost(in, out).orElse(null);
        return new LlmOutput(text, new Usage(cost, latency, out));
    }

    private String body(String prompt) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ObjectNode message = body.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", prompt);
        if (format != null) {
            body.set("format", format);
        }
        if (temperature != null || seed != null || contextSize != null) {
            ObjectNode options = body.putObject("options");
            if (temperature != null) {
                options.put("temperature", temperature);
            }
            if (seed != null) {
                options.put("seed", seed);
            }
            if (contextSize != null) {
                options.put("num_ctx", contextSize);
            }
        }
        return body.toString();
    }

    private static String withoutTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    private static Long count(JsonNode json, String field) {
        JsonNode n = json.get(field);
        return n != null && n.isIntegralNumber() ? n.longValue() : null;
    }

    private static String snippet(String body) {
        String s = body == null ? "" : body.strip();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /** Builds an {@link OllamaLlmClient}. */
    public static final class Builder {

        private URI baseUrl = DEFAULT_BASE_URL;
        private String model;
        private Double temperature;
        private Integer seed;
        private Integer contextSize;
        private JsonNode format;
        private Duration timeout = Duration.ofMinutes(5);
        private TokenPricing pricing;

        private Builder() {}

        /** @param baseUrl e.g. {@code http://localhost:11434} @return this */
        public Builder baseUrl(String baseUrl) {
            URI uri;
            try {
                uri = URI.create(baseUrl);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException("not a URL: " + baseUrl, e);
            }
            if (uri.getScheme() == null || !uri.getScheme().matches("https?") || uri.getHost() == null) {
                throw new IllegalArgumentException("base URL must be http(s)://host[:port]: " + baseUrl);
            }
            this.baseUrl = uri;
            return this;
        }

        /** @param model e.g. {@code qwen2.5:14b} @return this */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /** @param temperature sampling temperature, e.g. 0 @return this */
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /** @param seed sampling seed @return this */
        public Builder seed(int seed) {
            this.seed = seed;
            return this;
        }

        /**
         * The context window in tokens, sent as {@code num_ctx}. Set it above your longest prompt:
         * Ollama otherwise uses its own default (e.g. 4096) and silently drops the start of a
         * longer prompt, which is where the instructions usually are.
         *
         * @param tokens context size, e.g. 16384
         * @return this
         */
        public Builder contextSize(int tokens) {
            if (tokens <= 0) {
                throw new IllegalArgumentException("context size must be positive");
            }
            this.contextSize = tokens;
            return this;
        }

        /**
         * Constrains the answer's shape, sent as Ollama's {@code format}: {@code "json"} for any JSON
         * value, or a JSON Schema object for that exact shape. Ollama then can't answer with prose
         * or a code fence. It shapes what the model writes; the contract still judges every answer.
         *
         * @param format {@code json}, or a JSON Schema as JSON text
         * @return this
         */
        public Builder format(String format) {
            if (format == null || format.isBlank()) {
                throw new IllegalArgumentException("format must be \"json\" or a JSON Schema");
            }
            if (format.strip().equals("json")) {
                this.format = MAPPER.getNodeFactory().textNode("json");
                return this;
            }
            JsonNode schema;
            try {
                schema = MAPPER.readTree(format);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("format is neither \"json\" nor JSON Schema text: " + e.getOriginalMessage(), e);
            }
            if (schema == null || !schema.isObject()) {
                throw new IllegalArgumentException("a format schema must be a JSON object");
            }
            this.format = schema;
            return this;
        }

        /** @param timeout how long to wait for the whole answer (default 5 minutes) @return this */
        public Builder timeout(Duration timeout) {
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        /** @param pricing what you pay per token ({@code TokenPricing.FREE} for local); cost stays unknown without it @return this */
        public Builder pricing(TokenPricing pricing) {
            this.pricing = pricing;
            return this;
        }

        /** @return the client */
        public OllamaLlmClient build() {
            if (model == null || model.isBlank()) {
                throw new IllegalStateException("an Ollama model name is required, e.g. qwen2.5:14b");
            }
            return new OllamaLlmClient(this);
        }
    }
}
