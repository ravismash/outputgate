package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.List;

/**
 * Binds the output strictly to a Jackson type (R4). Unknown properties, nulls for
 * primitives, missing constructor properties, floats for integers and text for numbers
 * all fail, with the Jackson message. Evidence objects are read as their value first (R9).
 *
 * @param <T> the type the output must bind to
 */
public final class TypeGate<T> implements Gate {

    private static final String NAME = "type";
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private final ObjectReader reader;

    private TypeGate(Class<T> type) {
        this.reader = STRICT.readerFor(type);
    }

    /**
     * A gate that binds to {@code type}.
     *
     * @param type the target type, e.g. a record
     * @param <T> the target type
     * @return the gate
     */
    public static <T> TypeGate<T> of(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        return new TypeGate<>(type);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Violation> check(JsonNode output, EvalContext context) {
        try {
            reader.readValue(Paths.unwrapAll(output));
            return List.of();
        } catch (JsonMappingException e) {
            return List.of(new Violation(NAME, path(e), e.getOriginalMessage()));
        } catch (IOException e) {
            return List.of(new Violation(NAME, "", e.getMessage()));
        }
    }

    /** The field Jackson stopped at, in this library's path syntax ({@code lineItems[1].quantity}). */
    private static String path(JsonMappingException e) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
