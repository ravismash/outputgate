package io.github.ravismash.outputgate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A part of a source document, marked by the lines it starts and ends at: a table, a section, one
 * of several statements printed one after the other. Grounding proves a quote is somewhere in the
 * document; {@link ProvenanceGate.Builder#within} uses a region to also require that it comes from
 * the right part of it, so a value copied from a neighbouring table that prints the same labels is
 * rejected.
 *
 * <p>A region starts at the first line matching its start pattern (or at the document's first line)
 * and ends at the first line after that matching its end pattern (or at the document's last line
 * when it has none or no line matches). Patterns are regular expressions found anywhere in a line
 * of the document's text, as extracted; a region may run across pages. A start that no line
 * matches is a violation, never a pass.
 */
public final class Region {

    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private final Pattern start;
    private final Pattern end;
    private final boolean endInclusive;

    private Region(Pattern start, Pattern end, boolean endInclusive) {
        this.start = start;
        this.end = end;
        this.endInclusive = endInclusive;
    }

    /**
     * A region that starts at the first line of the document.
     *
     * @return the region, running to the end of the document until {@link #through} or {@link #before} says otherwise
     */
    public static Region fromDocumentStart() {
        return new Region(null, null, false);
    }

    /**
     * A region that starts at the first line matching {@code startRegex}, that line included (a
     * heading, a table's title, a section's name).
     *
     * @param startRegex a regular expression found in the starting line
     * @return the region, running to the end of the document until {@link #through} or {@link #before} says otherwise
     */
    public static Region from(String startRegex) {
        return new Region(Pattern.compile(startRegex), null, false);
    }

    /**
     * Ends the region at the first line after its start that matches {@code endRegex}, that line
     * included (a table's closing total). With no such line it runs to the end of the document.
     *
     * @param endRegex a regular expression found in the last line
     * @return a new region
     */
    public Region through(String endRegex) {
        return new Region(start, Pattern.compile(endRegex), true);
    }

    /**
     * Ends the region just before the first line after its start that matches {@code endRegex}
     * (the next section's heading). With no such line it runs to the end of the document.
     *
     * @param endRegex a regular expression found in the first line after the region
     * @return a new region
     */
    public Region before(String endRegex) {
        return new Region(start, Pattern.compile(endRegex), false);
    }

    @Override
    public String toString() {
        String from = start == null ? "from the start of the document" : "from the first line matching /" + start + "/";
        String to = end == null ? " to the end of the document"
                : (endInclusive ? " through" : " up to") + " the next line matching /" + end + "/";
        return from + to;
    }

    /**
     * Where the region lies in a document: its lines, as page and line numbers counted from 0.
     *
     * @param startPage the page of its first line
     * @param startLine its first line on that page
     * @param endPage the page of its last line
     * @param endLine its last line on that page
     */
    record Span(int startPage, int startLine, int endPage, int endLine) {

        /** @return where it lies in words, page and line numbers counted from 1 */
        String describe() {
            return startPage == endPage
                    ? "page " + (startPage + 1) + ", lines " + (startLine + 1) + " to " + (endLine + 1)
                    : "page " + (startPage + 1) + " line " + (startLine + 1) + " to page " + (endPage + 1) + " line " + (endLine + 1);
        }
    }

    /**
     * Finds the region in a document.
     *
     * @param document the document
     * @return where it lies, or empty when no line matches its start
     */
    Optional<Span> locate(SourceDocument document) {
        List<int[]> lines = new ArrayList<>(); // {page, line} for every line in reading order
        List<String> text = new ArrayList<>();
        for (int p = 0; p < document.pages().size(); p++) {
            String[] page = LINE_BREAK.split(document.pages().get(p), -1);
            for (int l = 0; l < page.length; l++) {
                lines.add(new int[] {p, l});
                text.add(page[l]);
            }
        }
        int first = 0;
        if (start != null) {
            first = -1;
            for (int i = 0; i < text.size() && first < 0; i++) {
                if (start.matcher(text.get(i)).find()) {
                    first = i;
                }
            }
            if (first < 0) {
                return Optional.empty();
            }
        }
        int last = text.size() - 1;
        if (end != null) {
            for (int i = first + 1; i < text.size(); i++) {
                if (end.matcher(text.get(i)).find()) {
                    last = endInclusive ? i : i - 1;
                    break;
                }
            }
        }
        return Optional.of(new Span(lines.get(first)[0], lines.get(first)[1], lines.get(last)[0], lines.get(last)[1]));
    }

    /**
     * The region's text on one page, its lines joined by line breaks.
     *
     * @param document the document
     * @param span where the region lies in it
     * @param page the page, counted from 0
     * @return the text, empty when the region has no line on that page
     */
    static String textOn(SourceDocument document, Span span, int page) {
        if (page < span.startPage() || page > span.endPage()) {
            return "";
        }
        String[] lines = LINE_BREAK.split(document.pages().get(page), -1);
        int from = page == span.startPage() ? span.startLine() : 0;
        int to = page == span.endPage() ? span.endLine() : lines.length - 1;
        return String.join("\n", List.of(lines).subList(from, to + 1));
    }
}
