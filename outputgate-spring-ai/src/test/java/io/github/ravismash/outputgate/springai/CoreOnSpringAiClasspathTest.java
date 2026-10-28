package io.github.ravismash.outputgate.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.SchemaGate;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.Violation;
import io.github.ravismash.outputgate.example.InvoiceExample;
import io.github.ravismash.outputgate.example.InvoiceExtractor;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;

/**
 * Every gate must keep working next to Spring AI's own dependencies. From 1.1, Spring AI brings its
 * own networknt validator: 2.x with 1.1 (which removed the classes SchemaGate needs) and 3.x with
 * 2.0. Core ships its own relocated copy, so the two never meet.
 */
class CoreOnSpringAiClasspathTest {

    @Test
    @DisplayName("this classpath really has Spring AI's networknt (the clash being tested is present)")
    void clashIsPresent() {
        assumeTrue(springAiAtLeast(1, 1), "Spring AI 1.0 brings no networknt, so there is no clash to test");
        ClassLoader loader = SchemaGate.class.getClassLoader();
        assertTrue(loader.getResource("com/networknt/schema/Schema.class") != null, "Spring AI's networknt 2.x or 3.x");
        assertNotEquals(true, loader.getResource("com/networknt/schema/JsonSchemaFactory.class") != null,
                "networknt 1.x must not be on this classpath");
    }

    /** Whether the Spring AI on this classpath is at least {@code major.minor}, from its jar's manifest. */
    private static boolean springAiAtLeast(int major, int minor) {
        String version = ChatClient.class.getPackage().getImplementationVersion();
        if (version == null) {
            return true;
        }
        String[] parts = version.split("[.-]");
        int maj = Integer.parseInt(parts[0]);
        return maj > major || maj == major && Integer.parseInt(parts[1]) >= minor;
    }

    @Test
    @DisplayName("SchemaGate works on a Spring AI classpath, with its own messages and paths")
    void schemaGateWorks() {
        String schema = """
                {"type": "object", "required": ["a"], "additionalProperties": false,
                 "properties": {"a": {"type": "string"}, "n": {"type": "array", "items": {"type": "integer"}}}}""";
        Contract c = Contract.builder().gate(SchemaGate.fromString(schema)).build();
        Verdict v = c.evaluate("{\"x\": 1, \"n\": [1, \"two\"]}");
        List<String> paths = v.violations().stream().map(Violation::path).toList();
        assertEquals(List.of("a", "n[1]", "x"), paths, v::toString);
        List<String> messages = v.violations().stream().map(Violation::message).toList();
        assertTrue(messages.get(0).startsWith("required:") && messages.get(0).contains("'a'"), messages::toString);
        assertTrue(messages.get(1).startsWith("type:") && messages.get(1).contains("integer"), messages::toString);
        assertTrue(messages.get(2).startsWith("additionalProperties:") && messages.get(2).contains("'x'"), messages::toString);
    }

    @Test
    @DisplayName("the full invoice contract judges the recorded real answer through Spring AI: it passes")
    void invoiceContractThroughSpringAi() throws Exception {
        try (ReplayServer server = new ReplayServer(Recording.RESOURCE)) {
            OllamaChatModel model = OllamaModels.at(server.baseUrl(), "qwen2.5:14b", null);
            LlmOutput out = new InvoiceExtractor(new SpringAiLlmClient(ChatClient.create(model), TokenPricing.FREE))
                    .extract(InvoiceExample.document());
            LlmAssert.assertThat(out).withContext(InvoiceExample.context()).passes(InvoiceExample.contract());
        }
    }
}
