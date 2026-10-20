package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.SourceDocument;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Writes the corrupted set: {@code count} broken copies of recorded outputs the full contract
 * accepts, spread evenly over every {@link Corruption} kind and over as many invoices as possible.
 * Starting only from accepted outputs means each corrupted output has exactly one known defect.
 * The same folder, count and seed always give the same files.
 */
public final class Corruptor {

    /** One line of corrupted/manifest.jsonl; {@code base} is the document's file name in results.jsonl. */
    public record Entry(String file, String base, String kind, String detail) {}

    /** What {@link #run} wrote. */
    public record Summary(int bases, List<Entry> entries, List<String> skipped) {}

    private Corruptor() {}

    /**
     * @param folder the documents and the EvalRun whose answers are corrupted
     * @param task the dataset's contract and schema
     * @param count how many corrupted outputs to write
     * @param seed fixes every random choice
     * @return what was written; fewer than {@code count} entries if the kinds ran out of invoices they apply to
     */
    public static Summary run(BenchmarkFolder folder, BenchmarkTask task, int count, long seed) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1");
        }
        Contract full = task.contract().get();
        List<String> required = task.requiredFields();
        List<String> skipped = new ArrayList<>();
        List<Base> bases = new ArrayList<>();
        BenchmarkFolder.Answers answers = folder.answers();
        for (Map.Entry<String, String> a : answers.byFile().entrySet()) {
            SourceDocument doc;
            try {
                doc = folder.document(a.getKey());
            } catch (IllegalArgumentException e) {
                skipped.add(a.getKey() + ": " + e.getMessage());
                continue;
            }
            if (full.evaluate(a.getValue(), EvalContext.empty().withSource(doc)).passed()) {
                bases.add(new Base(a.getKey(), a.getValue(), doc));
            }
        }
        if (bases.isEmpty()) {
            throw new IllegalStateException("no answer in " + folder.run().resolve("results.jsonl")
                    + " passes the full contract, so there is nothing correct to corrupt");
        }

        Random rnd = new Random(seed);
        Collections.shuffle(bases, rnd);
        Corruption[] kinds = Corruption.values();
        Set<String> used = new HashSet<>();
        Set<Corruption> exhausted = new HashSet<>();
        List<Entry> entries = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int cursor = 0;
        for (int slot = 0; entries.size() < count && exhausted.size() < kinds.length; slot++) {
            Corruption kind = kinds[slot % kinds.length];
            if (exhausted.contains(kind)) {
                continue;
            }
            Optional<Corruption.Result> made = Optional.empty();
            Base base = null;
            for (int tried = 0; tried < bases.size() && made.isEmpty(); tried++) {
                base = bases.get((cursor + tried) % bases.size());
                if (used.add(base.file + "--" + kind.id())) {
                    made = kind.apply(base.text, base.doc, required, rnd);
                }
            }
            if (made.isEmpty()) {
                exhausted.add(kind);
                continue;
            }
            cursor = (bases.indexOf(base) + 1) % bases.size();
            String file = BenchmarkFolder.stem(base.file) + "--" + kind.id() + ".txt";
            entries.add(new Entry(file, base.file, kind.id(), made.get().detail()));
            texts.add(made.get().text());
        }
        write(folder, entries, texts);
        return new Summary(bases.size(), entries, skipped);
    }

    /** @return the entries of corrupted/manifest.jsonl, or none if it does not exist */
    public static List<Entry> manifest(BenchmarkFolder folder) {
        if (!Files.isRegularFile(folder.manifest())) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        int n = 0;
        for (String line : BenchmarkFolder.read(folder.manifest()).split("\n")) {
            n++;
            if (line.isBlank()) {
                continue;
            }
            try {
                entries.add(Corruption.MAPPER.readValue(line, Entry.class));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(folder.manifest() + " line " + n + ": " + e.getOriginalMessage(), e);
            }
        }
        return entries;
    }

    private static void write(BenchmarkFolder folder, List<Entry> entries, List<String> texts) {
        try {
            Files.createDirectories(folder.corrupted());
            // Clear an earlier run so no stale file outlives the manifest that named it.
            for (Entry old : manifest(folder)) {
                Path stale = folder.corrupted().resolve(old.file()).normalize();
                if (stale.getParent() != null && stale.getParent().equals(folder.corrupted().normalize())) {
                    Files.deleteIfExists(stale);
                }
            }
            StringBuilder manifest = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                Files.writeString(folder.corrupted().resolve(e.file()), texts.get(i));
                ObjectNode line = Corruption.MAPPER.createObjectNode()
                        .put("file", e.file()).put("base", e.base()).put("kind", e.kind()).put("detail", e.detail());
                manifest.append(Corruption.MAPPER.writeValueAsString(line)).append('\n');
            }
            Files.writeString(folder.manifest(), manifest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Base(String file, String text, SourceDocument doc) {}
}
