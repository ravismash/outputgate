package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ravismash.outputgate.SourceDocument;

/** What every task's prompt and output schema share: evidence objects and numbered pages. */
final class Evidence {

    private Evidence() {}

    /**
     * @param json the mapper to build with
     * @param valueType the JSON Schema type of the value
     * @return the schema of {@code {value, source_span: {page, text}}}
     */
    static ObjectNode schema(ObjectMapper json, String valueType) {
        ObjectNode e = json.createObjectNode().put("type", "object");
        ObjectNode p = e.putObject("properties");
        p.putObject("value").put("type", valueType);
        ObjectNode span = p.putObject("source_span").put("type", "object");
        ObjectNode sp = span.putObject("properties");
        sp.putObject("page").put("type", "integer");
        sp.putObject("text").put("type", "string");
        span.putArray("required").add("page").add("text");
        e.putArray("required").add("value").add("source_span");
        return e;
    }

    /**
     * @param instructions the task's instructions
     * @param document the document
     * @return the instructions followed by each page under a "--- page N ---" line
     */
    static String withPages(String instructions, SourceDocument document) {
        StringBuilder p = new StringBuilder(instructions).append('\n');
        for (int i = 0; i < document.pages().size(); i++) {
            p.append("--- page ").append(i + 1).append(" ---\n").append(document.pages().get(i)).append('\n');
        }
        return p.toString();
    }
}
