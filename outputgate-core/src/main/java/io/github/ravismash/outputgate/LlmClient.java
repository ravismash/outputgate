package io.github.ravismash.outputgate;

/**
 * The one thing outputgate needs from a model (R10): send a prompt, get back the raw
 * text and the {@link Usage} of the call. Adapters (Spring AI, LangChain4j, Ollama) and
 * {@link FakeLlmClient} implement it. No gate ever calls a client.
 */
@FunctionalInterface
public interface LlmClient {

    /**
     * Sends {@code prompt} to the model.
     *
     * @param prompt the full prompt
     * @return the model's raw text and the call's usage
     */
    LlmOutput complete(String prompt);
}
