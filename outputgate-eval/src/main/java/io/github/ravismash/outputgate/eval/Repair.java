package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.Violation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One retry for every recorded answer the full contract rejects: the same prompt, the rejected
 * answer and every violation, word for word, and a request to return a corrected answer. The retry
 * is judged by the same contract, and against the ground truth when the documents have one.
 * Writes {@code repair.jsonl} and {@code repair.md} to the run folder; results.jsonl is left alone.
 */
public final class Repair {

    /** One rejected answer and its retry. */
    public record Attempt(String document, List<Violation> before, Verdict after, String output, String error,
            Benchmark.Truth truth) {

        /** @return whether the retry passed the full contract */
        public boolean recovered() {
            return after != null && after.passed();
        }
    }

    /** @param attempts every retry, in results.jsonl order */
    public record Summary(List<Attempt> attempts) {

        public int rejected() {
            return attempts.size();
        }

        public int recovered() {
            return (int) attempts.stream().filter(Attempt::recovered).count();
        }
    }

    private Repair() {}

    /**
     * @param taskPrompt the prompt the answer was recorded with
     * @param rejected the rejected answer
     * @param violations every violation the contract found in it
     * @return the retry prompt
     */
    static String prompt(String taskPrompt, String rejected, List<Violation> violations) {
        StringBuilder p = new StringBuilder(taskPrompt)
                .append("\n--- your previous answer ---\n").append(rejected)
                .append("\n--- it was rejected by these checks ---\n");
        for (Violation v : violations) {
            p.append("- [").append(v.gate()).append("] ").append(v.path().isEmpty() ? "(whole answer)" : v.path())
                    .append(": ").append(v.message()).append('\n');
        }
        return p.append("Fix every problem listed, using the document above, and return the whole corrected JSON object"
                + " and nothing else. Do not change values to make a check pass unless the document supports them.\n")
                .toString();
    }

    /**
     * @param folder the documents and the recorded run
     * @param task the dataset's contract and prompt
     * @param client the model that gets the retry (normally the one that recorded the run)
     * @param label how to name the model in repair.md
     * @return every retry
     */
    public static Summary run(BenchmarkFolder folder, BenchmarkTask task, LlmClient client, String label) {
        Contract contract = task.contract().get();
        List<Attempt> attempts = new ArrayList<>();
        Map<String, String> repaired = new LinkedHashMap<>();
        for (Map.Entry<String, String> a : folder.answers().byFile().entrySet()) {
            SourceDocument doc = folder.document(a.getKey());
            EvalContext ctx = EvalContext.empty().withSource(doc);
            Verdict first = contract.evaluate(a.getValue(), ctx);
            if (first.passed()) {
                continue;
            }
            String retry = prompt(task.prompt().apply(doc), a.getValue(), first.violations());
            try {
                LlmOutput out = client.complete(retry);
                attempts.add(new Attempt(a.getKey(), first.violations(), contract.evaluate(out, ctx), out.text(), null, null));
                repaired.put(a.getKey(), out.text());
            } catch (RuntimeException e) {
                attempts.add(new Attempt(a.getKey(), first.violations(), null, null,
                        e.getClass().getSimpleName() + ": " + e.getMessage(), null));
            }
            Attempt last = attempts.get(attempts.size() - 1);
            System.out.printf("%-40s %s%n", a.getKey(), last.recovered() ? "RECOVERED" : last.error() != null ? "ERROR" : "STILL REJECTED");
        }
        Map<String, Benchmark.Truth> truth = Benchmark.truth(folder, repaired);
        List<Attempt> withTruth = attempts.stream()
                .map(t -> new Attempt(t.document(), t.before(), t.after(), t.output(), t.error(), truth.get(t.document())))
                .toList();
        Summary s = new Summary(withTruth);
        write(folder, s, label);
        return s;
    }

    private static void write(BenchmarkFolder folder, Summary s, String label) {
        StringBuilder jsonl = new StringBuilder();
        StringBuilder md = new StringBuilder("# One retry with the gate errors: ").append(label).append("\n\n")
                .append(s.rejected()).append(" recorded answers were rejected by the full contract; each got one retry with "
                        + "its violations in the prompt. ").append(s.recovered()).append(" passed on the retry.\n\n")
                .append("| document | first answer rejected by | retry | retry against ground truth |\n|---|---|---|---|\n");
        for (Attempt a : s.attempts()) {
            ObjectNode line = Corruption.MAPPER.createObjectNode().put("file", a.document()).put("recovered", a.recovered());
            ArrayNode before = line.putArray("violations_before");
            a.before().forEach(v -> before.addObject().put("gate", v.gate()).put("path", v.path()).put("message", v.message()));
            if (a.after() != null) {
                ArrayNode after = line.putArray("violations_after");
                a.after().violations().forEach(v -> after.addObject().put("gate", v.gate()).put("path", v.path()).put("message", v.message()));
            }
            if (a.output() != null) {
                line.put("output", a.output());
            }
            if (a.error() != null) {
                line.put("error", a.error());
            }
            jsonl.append(line).append('\n');

            String retry = a.error() != null ? "error: " + a.error()
                    : a.recovered() ? "passed"
                    : "rejected: " + a.after().violations().stream().map(v -> "[" + v.gate() + "] " + v.message()).distinct()
                            .limit(2).reduce((x, y) -> x + "; " + y).orElse("");
            String truth = a.truth() == null ? "–"
                    : a.truth().wrong().isEmpty() ? "right on " + a.truth().fields().size() + " fields"
                    : "wrong: " + String.join("; ", a.truth().wrong());
            md.append("| ").append(a.document()).append(" | ")
                    .append(String.join(", ", a.before().stream().map(Violation::gate).distinct().toList())).append(" | ")
                    .append(retry.replace("|", "\\|")).append(" | ").append(truth.replace("|", "\\|")).append(" |\n");
        }
        try {
            Files.writeString(folder.run().resolve("repair.jsonl"), jsonl);
            Files.writeString(folder.run().resolve("repair.md"), md);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
