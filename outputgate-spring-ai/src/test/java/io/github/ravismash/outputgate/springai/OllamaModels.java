package io.github.ravismash.outputgate.springai;

import java.lang.reflect.Method;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;

/**
 * Builds a Spring AI {@link OllamaChatModel} on any Spring AI version in the README's matrix. Only
 * this test setup differs between versions, not the adapter: the options class is
 * {@code OllamaOptions} in 1.0, {@code OllamaChatOptions} from 1.1, and the builder method that
 * takes it is {@code defaultOptions} up to 1.1 and {@code options} from 2.0.
 */
final class OllamaModels {

    private OllamaModels() {}

    static OllamaChatModel at(String baseUrl, String model, Double temperature) throws ReflectiveOperationException {
        Class<?> optionsClass = firstClass("org.springframework.ai.ollama.api.OllamaChatOptions",
                "org.springframework.ai.ollama.api.OllamaOptions");
        Object options = optionsClass.getMethod("builder").invoke(null);
        options = options.getClass().getMethod("model", String.class).invoke(options, model);
        if (temperature != null) {
            options = options.getClass().getMethod("temperature", Double.class).invoke(options, temperature);
        }
        options = options.getClass().getMethod("build").invoke(options);

        OllamaChatModel.Builder builder = OllamaChatModel.builder().ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build());
        Method set;
        try {
            set = OllamaChatModel.Builder.class.getMethod("options", optionsClass);
        } catch (NoSuchMethodException e) {
            set = OllamaChatModel.Builder.class.getMethod("defaultOptions", optionsClass);
        }
        set.invoke(builder, options);
        return builder.build();
    }

    private static Class<?> firstClass(String... names) throws ClassNotFoundException {
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                // try the older name
            }
        }
        throw new ClassNotFoundException(String.join(" or ", names));
    }
}
