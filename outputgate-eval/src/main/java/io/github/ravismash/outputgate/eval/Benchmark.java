package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Verdict;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the three acceptance policies over the same outputs: the recorded (clean) answers and the
 * corrupted set. Nothing here calls a model; it reads the folder only.
 */
public final class Benchmark {

    /** How a pipeline decides whether to trust an output. */
    public enum Arm {
        /** What a naive prompt-and-parse pipeline does: accept anything a plain JSON parser reads as an object. */
        NAIVE("Naive (parse only)"),
        /** The task's JSON Schema, nothing else. */
        SCHEMA("Schema only"),
        /** The task's full contract, e.g. {@link FccInvoice#contract()}: schema, type, math, grounded provenance, regex. */
        FULL("Full contract");

        private final String label;

        Arm(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One output scored by every arm; {@code full} keeps the full contract's violations for the report. */
    public record Scored(String name, Map<Arm, Boolean> accepted, Verdict schema, Verdict full) {

        public boolean accepted(Arm arm) {
            return accepted.get(arm);
        }
    }

    /** A recorded answer for one document. */
    public record Clean(String document, Scored scored) {}

    /** One corrupted output, with what was broken. */
    public record Corrupt(Corruptor.Entry entry, Scored scored) {}

    /**
     * @param task the dataset's name
     * @param documents documents in the EvalRun
     * @param clean every document that could be read and has an answer
     * @param corrupted the corrupted set from the manifest
     * @param skipped documents left out, with the reason
     * @param model the EvalRun's label (usually the model), if any
     */
    public record Results(String task, int documents, List<Clean> clean, List<Corrupt> corrupted, List<String> skipped, Optional<String> model) {}

    private static final ObjectMapper PLAIN = new ObjectMapper();

    private Benchmark() {}

    /**
     * @param folder the documents and an EvalRun, usually with a corrupted set
     * @param task the dataset's contract and schema
     * @return every output scored by every arm
     */
    public static Results run(BenchmarkFolder folder, BenchmarkTask task) {
        Contract schema = task.schemaOnly();
        Contract full = task.contract().get();

        BenchmarkFolder.Answers answers = folder.answers();
        Map<String, SourceDocument> docs = new HashMap<>();
        List<String> skipped = new ArrayList<>(answers.skipped());
        List<Clean> clean = new ArrayList<>();
        for (Map.Entry<String, String> a : answers.byFile().entrySet()) {
            try {
                docs.put(a.getKey(), folder.document(a.getKey()));
            } catch (IllegalArgumentException e) {
                skipped.add(a.getKey() + ": " + e.getMessage());
                continue;
            }
            clean.add(new Clean(a.getKey(), score(a.getKey(), a.getValue(), docs.get(a.getKey()), schema, full)));
        }

        List<Corrupt> corrupted = new ArrayList<>();
        for (Corruptor.Entry e : Corruptor.manifest(folder)) {
            SourceDocument doc = docs.get(e.base());
            Path file = folder.corrupted().resolve(e.file());
            if (doc == null || !Files.isRegularFile(file)) {
                skipped.add("corrupted " + e.file() + ": its document or file is missing");
                continue;
            }
            corrupted.add(new Corrupt(e, score(e.file(), BenchmarkFolder.read(file), doc, schema, full)));
        }
        return new Results(task.name(), answers.byFile().size() + answers.skipped().size(), clean, corrupted, skipped, folder.label());
    }

    static Scored score(String name, String text, SourceDocument doc, Contract schema, Contract full) {
        EvalContext ctx = EvalContext.empty().withSource(doc);
        Verdict s = schema.evaluate(text, ctx);
        Verdict f = full.evaluate(text, ctx);
        return new Scored(name, Map.of(Arm.NAIVE, naiveAccepts(text), Arm.SCHEMA, s.passed(), Arm.FULL, f.passed()), s, f);
    }

    /** A default Jackson parser: it ignores anything after the first JSON value, as most pipelines do. */
    static boolean naiveAccepts(String text) {
        try {
            JsonNode tree = PLAIN.readTree(text == null ? "" : text);
            return tree != null && tree.isObject();
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
