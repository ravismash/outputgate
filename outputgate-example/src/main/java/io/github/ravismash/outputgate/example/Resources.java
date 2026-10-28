package io.github.ravismash.outputgate.example;

import io.github.ravismash.outputgate.SourceDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Reads the examples' source documents from the classpath; pages are separated by a form feed. */
final class Resources {

    private Resources() {}

    static SourceDocument document(String resource) {
        try (InputStream in = Resources.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + resource);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> pages = Arrays.stream(text.split("\f")).map(String::strip).toList();
            return new SourceDocument(pages);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
