package io.github.ravismash.outputgate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates the output against a JSON Schema 2020-12 (R3), loaded from a string or the
 * classpath. Evidence objects ({@code {"value": ...}}) are read as their value first (R9).
 * Each violation names the schema keyword that failed and the field it concerns. The schema
 * itself is checked against the 2020-12 meta-schema when the gate is built.
 */
public final class SchemaGate implements Gate {

    private static final String NAME = "schema";
    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    /** The official 2020-12 meta-schema; networknt resolves it from its own bundled copy, not the network. */
    private static final JsonSchema META_SCHEMA = FACTORY.getSchema(SchemaLocation.of(DRAFT_2020_12));

    private final JsonSchema schema;

    private SchemaGate(JsonSchema schema) {
        this.schema = schema;
    }

    /**
     * A gate for the schema in {@code schemaJson}.
     *
     * @param schemaJson a JSON Schema 2020-12 document
     * @return the gate
     * @throws IllegalArgumentException if the text is not JSON, declares another draft, or is not a valid schema
     */
    public static SchemaGate fromString(String schemaJson) {
        JsonNode node;
        try {
            node = Json.parse(schemaJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("schema is not JSON: " + e.getOriginalMessage(), e);
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("schema must be a JSON object");
        }
        JsonNode declared = node.get("$schema");
        if (declared != null && !(declared.isTextual() && declared.asText().replaceAll("#$", "").equals(DRAFT_2020_12))) {
            throw new IllegalArgumentException("schema must be JSON Schema 2020-12, but declares " + declared);
        }
        // A malformed keyword (e.g. "required": "vendor") would otherwise be ignored and let
        // every output through, so the schema itself is checked against the meta-schema first.
        Set<ValidationMessage> problems = META_SCHEMA.validate(node);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("not a valid JSON Schema 2020-12: " + problems.stream()
                    .map(m -> m.getType() + ": " + m.getMessage()).sorted().collect(Collectors.joining("; ")));
        }
        try {
            JsonSchema schema = FACTORY.getSchema(node);
            schema.initializeValidators();
            return new SchemaGate(schema);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a valid JSON Schema 2020-12: " + e.getMessage(), e);
        }
    }

    /**
     * A gate for the schema at {@code resource} on the classpath.
     *
     * @param resource a classpath resource name, e.g. {@code schemas/invoice.schema.json}
     * @return the gate
     * @throws IllegalArgumentException if the resource is missing or is not a valid 2020-12 schema
     */
    public static SchemaGate fromClasspath(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = SchemaGate.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("schema not found on the classpath: " + resource);
            }
            return fromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("could not read schema " + resource + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Violation> check(JsonNode output, EvalContext context) {
        Set<ValidationMessage> messages = schema.validate(Paths.unwrapAll(output));
        return messages.stream()
                .map(m -> new Violation(NAME, path(m), m.getType() + ": " + m.getMessage()))
                .sorted(Comparator.comparing(Violation::path).thenComparing(Violation::message))
                .toList();
    }

    /** The field a message concerns, in this library's path syntax ({@code line_items[1].amount}). */
    private static String path(ValidationMessage m) {
        StringBuilder path = new StringBuilder();
        JsonNodePath location = m.getInstanceLocation();
        for (int i = 0; i < location.getNameCount(); i++) {
            Object element = location.getElement(i);
            if (element instanceof Integer index) {
                path.append('[').append(index).append(']');
            } else {
                appendField(path, String.valueOf(element));
            }
        }
        String type = m.getType();
        if (m.getProperty() != null && ("required".equals(type) || "additionalProperties".equals(type))) {
            appendField(path, m.getProperty());
        }
        return path.toString();
    }

    private static void appendField(StringBuilder path, String name) {
        if (!path.isEmpty()) {
            path.append('.');
        }
        path.append(name);
    }
}
