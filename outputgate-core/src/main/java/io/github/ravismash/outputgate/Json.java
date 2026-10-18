package io.github.ravismash.outputgate;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

/** The one strict parser every contract uses. */
final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .build();

    private Json() {}

    /** Parses the whole text as one JSON value, or throws. Empty text is not JSON. */
    static JsonNode parse(String raw) throws JsonProcessingException {
        JsonNode tree = MAPPER.readTree(raw == null ? "" : raw);
        if (tree == null || tree.isMissingNode()) {
            throw new JsonProcessingException("output is empty") {};
        }
        return tree;
    }
}
