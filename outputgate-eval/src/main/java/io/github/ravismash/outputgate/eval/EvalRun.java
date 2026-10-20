package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.GateResult;
import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.Violation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Runs a model over a folder of PDFs and judges every answer with a contract (FRD Days 12–13).
 * Every case is logged: rejections with gate, path and message, the raw answer, usage and errors.
 * Writes {@code results.jsonl} and {@code summary.md} to the output folder.
 */
public final class EvalRun {

    /** How one PDF went. */
    public enum Outcome {
        /** The answer passed every gate. */
        PASSED,
        /** The answer was rejected; see the violations. */
        REJECTED,
        /** The PDF has no text layer, so no prompt was sent. */
        NO_TEXT,
        /** The model call failed; see the error. */
        ERROR
    }

    /**
     * One PDF's result.
     *
     * @param file the PDF's file name
     * @param pages page count (0 when unreadable)
     * @param promptChars length of the prompt sent (0 when none)
     * @param outcome what happened
     * @param outputTokens tokens the model produced, or null when unknown
     * @param latencyMillis call time, or null when no call was made
     * @param violations every violation, empty unless rejected
     * @param error the error message for NO_TEXT and ERROR, otherwise null
     * @param output the model's raw answer, or null when there was none
     */
    public record Result(String file, int pages, int promptChars, Outcome outcome, Long outputTokens, Long latencyMillis,
            List<Violation> violations, String error, String output) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private EvalRun() {}

    /**
     * @param pdfDir folder of PDFs (sorted by name)
     * @param client the model
     * @param contract the contract that judges each answer
     * @param prompt builds the prompt from a document
     * @param outDir where results.jsonl and summary.md go (created)
     * @param label a name for the run in the summary, e.g. the model
     * @return one result per PDF
     */
    public static List<Result> run(Path pdfDir, LlmClient client, Contract contract,
            Function<SourceDocument, String> prompt, Path outDir, String label) {
        List<Path> pdfs;
        try (Stream<Path> files = Files.list(pdfDir)) {
            pdfs = files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf")).sorted().toList();
            Files.createDirectories(outDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Result> results = new ArrayList<>();
        Path jsonl = outDir.resolve("results.jsonl");
        try {
            Files.writeString(jsonl, "");
            for (Path pdf : pdfs) {
                Result r = one(pdf, client, contract, prompt);
                results.add(r);
                Files.writeString(jsonl, json(r) + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
                System.out.printf("%-48s %-8s %s%n", r.file(), r.outcome(),
                        r.outcome() == Outcome.REJECTED ? failingGates(r) : r.error() == null ? "" : r.error());
            }
            Files.writeString(outDir.resolve("summary.md"), summary(results, label, pdfDir), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return results;
    }

    private static Result one(Path pdf, LlmClient client, Contract contract, Function<SourceDocument, String> prompt) {
        String name = pdf.getFileName().toString();
        SourceDocument doc;
        try {
            doc = PdfText.load(pdf);
        } catch (IllegalArgumentException e) {
            return new Result(name, 0, 0, Outcome.NO_TEXT, null, null, List.of(), e.getMessage(), null);
        }
        String text = prompt.apply(doc);
        LlmOutput out;
        try {
            out = client.complete(text);
        } catch (RuntimeException e) {
            return new Result(name, doc.pages().size(), text.length(), Outcome.ERROR, null, null, List.of(),
                    e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
        Verdict v = contract.evaluate(out, EvalContext.empty().withSource(doc));
        Long millis = out.usage().latency() == null ? null : out.usage().latency().toMillis();
        return new Result(name, doc.pages().size(), text.length(), v.passed() ? Outcome.PASSED : Outcome.REJECTED,
                out.usage().outputTokens(), millis, v.violations(), null, out.text());
    }

    private static List<String> failingGates(Result r) {
        return r.violations().stream().map(Violation::gate).distinct().toList();
    }

    private static String json(Result r) {
        ObjectNode n = JSON.createObjectNode();
        n.put("file", r.file());
        n.put("pages", r.pages());
        n.put("prompt_chars", r.promptChars());
        n.put("outcome", r.outcome().name());
        if (r.outputTokens() != null) {
            n.put("output_tokens", r.outputTokens());
        }
        if (r.latencyMillis() != null) {
            n.put("latency_ms", r.latencyMillis());
        }
        ArrayNode vs = n.putArray("violations");
        for (Violation v : r.violations()) {
            vs.addObject().put("gate", v.gate()).put("path", v.path()).put("message", v.message());
        }
        if (r.error() != null) {
            n.put("error", r.error());
        }
        if (r.output() != null) {
            n.put("output", r.output());
        }
        return n.toString();
    }

    private static String summary(List<Result> results, String label, Path pdfDir) {
        Map<Outcome, Long> outcomes = new TreeMap<>();
        for (Outcome o : Outcome.values()) {
            outcomes.put(o, results.stream().filter(r -> r.outcome() == o).count());
        }
        Map<String, Integer> byGate = new TreeMap<>();
        for (Result r : results) {
            failingGates(r).forEach(g -> byGate.merge(g, 1, Integer::sum));
        }
        long tokens = results.stream().filter(r -> r.outputTokens() != null).mapToLong(Result::outputTokens).sum();
        long millis = results.stream().filter(r -> r.latencyMillis() != null).mapToLong(Result::latencyMillis).sum();

        StringBuilder s = new StringBuilder();
        s.append("# Eval run: ").append(label).append("\n\n")
                .append("- When: ").append(OffsetDateTime.now()).append('\n')
                .append("- PDFs: ").append(results.size()).append(" from `").append(pdfDir).append("`\n")
                .append("- Outcomes: ");
        outcomes.forEach((o, c) -> s.append(o).append(": ").append(c).append(" · "));
        s.setLength(s.length() - 3);
        s.append("\n- Output tokens: ").append(tokens).append(" · model time: ").append(millis / 1000).append(" s\n\n");
        s.append("## Rejections by gate (PDFs where the gate failed)\n\n| gate | PDFs |\n|---|---|\n");
        byGate.forEach((g, c) -> s.append("| ").append(g).append(" | ").append(c).append(" |\n"));
        s.append("\n## Every PDF\n\n| file | pages | outcome | output tokens | seconds | failing gates |\n|---|---|---|---|---|---|\n");
        for (Result r : results) {
            s.append("| ").append(r.file()).append(" | ").append(r.pages()).append(" | ").append(r.outcome())
                    .append(" | ").append(r.outputTokens() == null ? "" : r.outputTokens())
                    .append(" | ").append(r.latencyMillis() == null ? "" : r.latencyMillis() / 1000)
                    .append(" | ").append(String.join(", ", failingGates(r))).append(" |\n");
        }
        s.append("\n## Every violation and error\n\n");
        for (Result r : results) {
            for (Violation v : r.violations()) {
                s.append("- `").append(r.file()).append("` [").append(v.gate()).append("] ")
                        .append(v.path().isEmpty() ? "(whole output)" : v.path()).append(": ").append(v.message()).append('\n');
            }
            if (r.error() != null) {
                s.append("- `").append(r.file()).append("` ").append(r.outcome()).append(": ").append(r.error()).append('\n');
            }
        }
        return s.toString();
    }
}
