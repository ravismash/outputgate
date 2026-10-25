package io.github.ravismash.outputgate.eval;

import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.ollama.OllamaLlmClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The benchmark from the command line, over the documents and the answers an {@link EvalRun}
 * recorded for them (see {@link BenchmarkFolder}). Only {@code record} calls a model:
 *
 * <pre>
 * record  DOCUMENTS RUN --model M [--task fcc] [--client ollama|openai] [--base-url URL] [--ctx 16384]
 *         runs the model over every document and writes RUN/results.jsonl and summary.md
 *         (ollama: a local Ollama; openai: an OpenAI-compatible server such as LM Studio)
 * corrupt DOCUMENTS RUN [--task fcc] [--count 50] [--seed 42]
 *         writes RUN/corrupted/ from the answers the full contract accepts
 * report  DOCUMENTS RUN [--task fcc]
 *         scores the answers and the corrupted set three ways; writes RUN/benchmark.md and .html
 * </pre>
 */
public final class BenchmarkCli {

    static final String USAGE = """
            usage: benchmark record  DOCUMENTS RUN --model M [--task fcc] [--client ollama|openai] [--base-url URL] [--ctx 16384]
                   benchmark corrupt DOCUMENTS RUN [--task fcc] [--count 50] [--seed 42]
                   benchmark report  DOCUMENTS RUN [--task fcc]
            RUN is an EvalRun output folder (results.jsonl), written by record.""";

    private BenchmarkCli() {}

    /** @param args a command, the documents folder, the run folder and options */
    public static void main(String[] args) {
        try {
            System.out.println(run(args));
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    /** Runs one command. @return what to print */
    static String run(String... args) {
        if (args.length < 3) {
            throw new IllegalArgumentException(USAGE);
        }
        Map<String, String> opts = new HashMap<>();
        for (int i = 3; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 == args.length) {
                throw new IllegalArgumentException("bad option " + args[i] + "\n" + USAGE);
            }
            opts.put(args[i].substring(2), args[++i]);
        }
        BenchmarkTask task = BenchmarkTask.byName(opts.getOrDefault("task", BenchmarkTask.FCC.name()));
        if (args[0].equals("record")) {
            return record(Path.of(args[1]).toAbsolutePath(), Path.of(args[2]).toAbsolutePath(), task, opts);
        }
        BenchmarkFolder folder = new BenchmarkFolder(Path.of(args[1]).toAbsolutePath(), Path.of(args[2]).toAbsolutePath());
        return switch (args[0]) {
            case "corrupt" -> {
                Corruptor.Summary s = Corruptor.run(folder, task, Integer.parseInt(opts.getOrDefault("count", "50")),
                        Long.parseLong(opts.getOrDefault("seed", "42")));
                yield "wrote " + s.entries().size() + " corrupted outputs from " + s.bases()
                        + " accepted answers to " + folder.corrupted() + skipped(s.skipped().size());
            }
            case "report" -> {
                Benchmark.Results r = Benchmark.run(folder, task);
                Path md = BenchmarkReport.write(r, folder.run());
                yield BenchmarkReport.markdown(r) + "\nwrote " + md + " and benchmark.html" + skipped(r.skipped().size());
            }
            default -> throw new IllegalArgumentException("unknown command " + args[0] + "\n" + USAGE);
        };
    }

    private static String record(Path documents, Path run, BenchmarkTask task, Map<String, String> opts) {
        String model = opts.get("model");
        if (model == null) {
            throw new IllegalArgumentException("record needs --model\n" + USAGE);
        }
        String schema = task.outputSchema() == null ? null : task.outputSchema().get();
        String client = opts.getOrDefault("client", "ollama");
        LlmClient llm;
        String label;
        switch (client) {
            case "ollama" -> {
                int ctx = Integer.parseInt(opts.getOrDefault("ctx", "16384"));
                OllamaLlmClient.Builder b = OllamaLlmClient.builder().model(model).temperature(0.0).seed(42).contextSize(ctx)
                        .timeout(Duration.ofMinutes(15)).pricing(TokenPricing.FREE)
                        .baseUrl(opts.getOrDefault("base-url", "http://localhost:11434"));
                if (schema != null) {
                    b.format(schema);
                }
                llm = b.build();
                label = model + " (local Ollama, temperature 0, seed 42, num_ctx " + ctx
                        + (schema == null ? "" : ", format: " + task.name() + " output schema") + ")";
            }
            case "openai" -> {
                String url = opts.getOrDefault("base-url", "http://localhost:1234");
                llm = new OpenAiCompatibleClient(url, model, schema, Duration.ofMinutes(15));
                label = model + " (OpenAI-compatible server at " + url + ", temperature 0"
                        + (schema == null ? "" : ", response_format: " + task.name() + " output schema") + ")";
            }
            default -> throw new IllegalArgumentException("unknown client " + client + "; use ollama or openai");
        }
        List<EvalRun.Result> results = EvalRun.run(documents, llm, task.contract().get(), task.prompt(), run, label);
        long passed = results.stream().filter(r -> r.outcome() == EvalRun.Outcome.PASSED).count();
        return "recorded " + results.size() + " documents, " + passed + " passed the full contract; wrote " + run.resolve("summary.md");
    }

    private static String skipped(int n) {
        return n == 0 ? "" : "; " + n + " skipped (listed in the report)";
    }
}
