package io.github.ravismash.outputgate.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

/** The recorded qwen2.5:14b answer to the invoice prompt (local Ollama 0.34.4, 2026-10-19). */
final class Recording {

    static final String RESOURCE = "recorded/ollama-chat-invoice.json";
    static final String REQUEST = "recorded/ollama-chat-invoice.request.json";

    private Recording() {}

    static JsonNode json(String resource) throws IOException {
        try (InputStream in = Recording.class.getClassLoader().getResourceAsStream(resource)) {
            return new ObjectMapper().readTree(in);
        }
    }

    static String content() throws IOException {
        return json(RESOURCE).path("message").path("content").asText();
    }

    static String prompt() throws IOException {
        return json(REQUEST).path("messages").get(0).path("content").asText();
    }

    static long promptTokens() throws IOException {
        return json(RESOURCE).path("prompt_eval_count").asLong();
    }

    static long outputTokens() throws IOException {
        return json(RESOURCE).path("eval_count").asLong();
    }
}
