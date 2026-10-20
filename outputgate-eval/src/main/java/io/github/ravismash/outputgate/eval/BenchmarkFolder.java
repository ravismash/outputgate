package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.ravismash.outputgate.SourceDocument;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where a benchmark reads and writes. The model's answers come from an {@link EvalRun}, so the
 * benchmark itself never calls a model and re-runs offline:
 *
 * <pre>
 * documents/                   the documents: PDFs with a text layer (what EvalRun reads), or .txt
 *   581482.pdf                 files with pages split by form feeds (e.g. OCR text of a receipt)
 * run/                         EvalRun's output folder
 *   results.jsonl, summary.md  written by EvalRun: one answer per document
 *   corrupted/581482--number-typo.txt
 *   corrupted/manifest.jsonl   written by {@code corrupt}: one line per corrupted output
 *   benchmark.md, benchmark.html  written by {@code report}
 * </pre>
 *
 * @param documents the folder EvalRun read
 * @param run EvalRun's output folder
 */
public record BenchmarkFolder(Path documents, Path run) {

    /** Checks both folders exist and the run has its results. */
    public BenchmarkFolder {
        for (Path p : new Path[] {documents, run}) {
            if (p == null || !Files.isDirectory(p)) {
                throw new IllegalArgumentException("no such folder: " + p);
            }
        }
        if (!Files.isRegularFile(run.resolve("results.jsonl"))) {
            throw new IllegalArgumentException("no results.jsonl in " + run + "; run EvalRun (e.g. runFccOllama) first");
        }
    }

    /** What EvalRun recorded: the answers, and the documents it got none for. */
    public record Answers(Map<String, String> byFile, List<String> skipped) {}

    /**
     * @return each document's raw answer by file name, in results.jsonl order, and the documents with no answer
     * @throws IllegalArgumentException if a line is not JSON or has no file name
     */
    public Answers answers() {
        Map<String, String> byFile = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        int n = 0;
        for (String line : read(run.resolve("results.jsonl")).split("\n")) {
            n++;
            if (line.isBlank()) {
                continue;
            }
            JsonNode r;
            try {
                r = Corruption.MAPPER.readTree(line);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("results.jsonl line " + n + ": " + e.getOriginalMessage(), e);
            }
            String file = r.path("file").asText("");
            if (file.isEmpty()) {
                throw new IllegalArgumentException("results.jsonl line " + n + " has no file");
            }
            if (r.path("output").isTextual()) {
                byFile.put(file, r.get("output").asText());
            } else {
                skipped.add((file + ": " + r.path("outcome").asText("no answer") + " " + r.path("error").asText("")).strip());
            }
        }
        return new Answers(byFile, skipped);
    }

    /** @param file a file name from results.jsonl @return the document's text */
    public SourceDocument document(String file) {
        return load(documents.resolve(file));
    }

    /** @return the run's label, from the first line of EvalRun's summary.md ("# Eval run: ...") */
    public Optional<String> label() {
        Path summary = run.resolve("summary.md");
        if (!Files.isRegularFile(summary)) {
            return Optional.empty();
        }
        String first = read(summary).lines().findFirst().orElse("");
        return first.startsWith("# Eval run: ") ? Optional.of(first.substring("# Eval run: ".length()).strip()) : Optional.empty();
    }

    /**
     * Reads a document's text. A PDF goes through {@link PdfText}; a .txt file is split into pages
     * at form feeds ({@code \f}).
     *
     * @param document a .pdf or .txt file
     * @return its pages
     * @throws IllegalArgumentException if the document is missing, of another type or has no text
     */
    public static SourceDocument load(Path document) {
        if (isPdf(document)) {
            return PdfText.load(document);
        }
        if (!isText(document)) {
            throw new IllegalArgumentException("not a .pdf or .txt document: " + document);
        }
        String text;
        try {
            text = Files.readString(document);
        } catch (IOException e) {
            throw new IllegalArgumentException("can't read " + document.getFileName() + ": " + e.getMessage(), e);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException(document.getFileName() + " has no text");
        }
        return new SourceDocument(List.of(text.split("\f", -1)));
    }

    /** @param p a path @return whether it names a PDF */
    public static boolean isPdf(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /** @param p a path @return whether it names a .txt document */
    public static boolean isText(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt");
    }

    /** @param file a document's file name @return the name without its extension */
    public static String stem(String file) {
        int dot = file.lastIndexOf('.');
        return dot < 0 ? file : file.substring(0, dot);
    }

    public Path corrupted() {
        return run.resolve("corrupted");
    }

    public Path manifest() {
        return corrupted().resolve("manifest.jsonl");
    }

    static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
