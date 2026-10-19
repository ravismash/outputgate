package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;

/** NFR-2 / NFR-3: adapters are separate modules; nothing of theirs is on core's classpath. */
class CoreStaysLightTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "org.springframework.ai.chat.client.ChatClient",
        "dev.langchain4j.model.chat.ChatModel",
        "org.springframework.context.ApplicationContext"
    })
    @DisplayName("NFR-3: no adapter or framework class is visible from core")
    void noFrameworkOnCoreClasspath(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
