package io.github.ravismash.outputgate.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * Every gate must keep working next to Spring AI's own dependencies. Spring AI 1.1.8 pulls
 * networknt 2.0.0, which removed the classes SchemaGate needs; core ships its own relocated
 * copy, so the two never meet.
 */
class CoreOnSpringAiClasspathTest {

    @Test
    @DisplayName("this classpath really has Spring AI's networknt (the clash being tested is present)")
    void clashIsPresent() throws Exception {
        Class<?> theirs = Class.forName("com.networknt.schema.Schema");
        assertNotEquals(SchemaGate.class.getClassLoader().getResource("com/networknt/schema/JsonSchemaFactory.class") != null,
                true, "networknt 1.x must not be on this classpath");
        assertTrue(theirs.getName().startsWith("com.networknt"));
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
            OllamaChatModel model = OllamaChatModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(server.baseUrl()).build())
                    .defaultOptions(OllamaChatOptions.builder().model("qwen2.5:14b").build())
                    .build();
            LlmOutput out = new InvoiceExtractor(new SpringAiLlmClient(ChatClient.create(model), TokenPricing.FREE))
                    .extract(InvoiceExample.document());
            LlmAssert.assertThat(out).withContext(InvoiceExample.context()).passes(InvoiceExample.contract());
        }
    }
}
