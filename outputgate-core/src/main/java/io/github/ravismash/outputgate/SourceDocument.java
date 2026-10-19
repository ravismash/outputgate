package io.github.ravismash.outputgate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The document an output was extracted from, page by page, for grounding (R6).
 *
 * @param pages the text of each page; page 1 is {@code pages.get(0)}
 */
public record SourceDocument(List<String> pages) {

    /** Copies the list and rejects a document with no pages. */
    public SourceDocument {
        if (pages == null || pages.isEmpty() || pages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("a source document needs at least one page of text");
        }
        pages = List.copyOf(pages);
    }

    /**
     * A one-page document.
     *
     * @param text the whole text
     * @return the document
     */
    public static SourceDocument of(String text) {
        return new SourceDocument(List.of(text == null ? "" : text));
    }

    /**
     * A document with these pages, in order.
     *
     * @param pages the text of each page
     * @return the document
     */
    public static SourceDocument ofPages(String... pages) {
        return new SourceDocument(pages == null ? List.of() : Arrays.asList(pages));
    }
}
