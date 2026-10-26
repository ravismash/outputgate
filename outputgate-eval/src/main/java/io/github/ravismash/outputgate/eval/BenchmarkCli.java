package io.github.ravismash.outputgate.eval;

import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.ollama.OllamaLlmClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The benchmark from the command line, over the documents and the answers an {@link EvalRun}
 * recorded for them (see {@link BenchmarkFolder}). Only {@code record} and {@code repair} call a model:
 *
 * <pre>
 * record  DOCUMENTS RUN --model M [--task fcc] [--client ollama|openai|gemini] [--base-url URL] [--ctx 16384] [--resume true]
 *         runs the model over every document and writes RUN/results.jsonl and summary.md;
 *         --resume true keeps the documents a stopped run already recorded
 *         (ollama: a local Ollama; openai: an OpenAI-compatible server such as LM Studio;
 *         gemini: the Gemini API, key from GEMINI_API_KEY or ~/.gemini_api_key, or --api-key-file)
 * repair  DOCUMENTS RUN --model M [--task fcc] [--client ...]  (the same model options as record)
 *         gives every answer the full contract rejects one retry, with the violations in the prompt;
 *         writes RUN/repair.jsonl and repair.md
 * corrupt DOCUMENTS RUN [--task fcc] [--count 50] [--seed 42]
 *         writes RUN/corrupted/ from the answers the full contract accepts
 * report  DOCUMENTS RUN [--task fcc]
 *         scores the answers and the corrupted set three ways; writes RUN/benchmark.md and .html
 * </pre>
 */
public final class BenchmarkCli {

    static final String USAGE = """
            usage: benchmark record  DOCUMENTS RUN --model M [--task fcc] [--client ollama|openai|gemini] [--base-url URL] [--ctx 16384] [--resume true] [--api-key-file F]
                   benchmark repair  DOCUMENTS RUN --model M [--task fcc] [same options as record]
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
        if (args[0].equals("repair")) {
            Model m = model(task, opts, "repair");
            Repair.Summary s = Repair.run(folder, task, m.client(), m.label());
            return s.rejected() + " answers rejected; " + s.recovered() + " passed after one retry; wrote "
                    + folder.run().resolve("repair.md");
        }
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

    /** A model client and how to describe it in a report. */
    record Model(LlmClient client, String label) {}

    private static String record(Path documents, Path run, BenchmarkTask task, Map<String, String> opts) {
        Model m = model(task, opts, "record");
        List<EvalRun.Result> results = EvalRun.run(documents, m.client(), task.contract().get(), task.prompt(), run, m.label(),
                Boolean.parseBoolean(opts.getOrDefault("resume", "false")));
        long passed = results.stream().filter(r -> r.outcome() == EvalRun.Outcome.PASSED).count();
        return "recorded " + results.size() + " documents, " + passed + " passed the full contract; wrote " + run.resolve("summary.md");
    }

    /** Builds the client --client, --model, --base-url and --ctx describe. */
    static Model model(BenchmarkTask task, Map<String, String> opts, String command) {
        String model = opts.get("model");
        if (model == null) {
            throw new IllegalArgumentException(command + " needs --model\n" + USAGE);
        }
        String schema = task.outputSchema() == null ? null : task.outputSchema().get();
        String client = opts.getOrDefault("client", "ollama");
        switch (client) {
            case "ollama" -> {
                int ctx = Integer.parseInt(opts.getOrDefault("ctx", "16384"));
                OllamaLlmClient.Builder b = OllamaLlmClient.builder().model(model).temperature(0.0).seed(42).contextSize(ctx)
                        .timeout(Duration.ofMinutes(15)).pricing(TokenPricing.FREE)
                        .baseUrl(opts.getOrDefault("base-url", "http://localhost:11434"));
                if (schema != null) {
                    b.format(schema);
                }
                return new Model(b.build(), model + " (local Ollama, temperature 0, seed 42, num_ctx " + ctx
                        + (schema == null ? "" : ", format: " + task.name() + " output schema") + ")");
            }
            case "openai", "gemini" -> {
                boolean gemini = client.equals("gemini");
                String url = opts.getOrDefault("base-url",
                        gemini ? "https://generativelanguage.googleapis.com/v1beta/openai" : "http://localhost:1234/v1");
                return new Model(new OpenAiCompatibleClient(url, model, schema, Duration.ofMinutes(15), apiKey(opts, gemini),
                        gemini ? null : 42),
                        model + " (" + (gemini ? "Gemini API" : "OpenAI-compatible server at " + url) + ", temperature 0"
                        + (gemini ? "" : ", seed 42")
                        + (schema == null ? "" : ", response_format: " + task.name() + " output schema") + ")");
            }
            default -> throw new IllegalArgumentException("unknown client " + client + "; use ollama, openai or gemini");
        }
    }

    /** The key from --api-key-file, else GEMINI_API_KEY or ~/.gemini_api_key for gemini, else OPENAI_API_KEY; null if none. */
    private static String apiKey(Map<String, String> opts, boolean gemini) {
        Path file = opts.containsKey("api-key-file") ? Path.of(opts.get("api-key-file"))
                : gemini ? Path.of(System.getProperty("user.home"), ".gemini_api_key") : null;
        String env = System.getenv(gemini ? "GEMINI_API_KEY" : "OPENAI_API_KEY");
        if (!opts.containsKey("api-key-file") && env != null && !env.isBlank()) {
            return env.strip();
        }
        if (file != null && Files.isRegularFile(file)) {
            try {
                return Files.readString(file).strip();
            } catch (IOException e) {
                throw new IllegalArgumentException("can't read " + file + ": " + e.getMessage(), e);
            }
        }
        if (gemini) {
            throw new IllegalArgumentException("no Gemini API key: set GEMINI_API_KEY or save it to ~/.gemini_api_key");
        }
        return null;
    }

    private static String skipped(int n) {
        return n == 0 ? "" : "; " + n + " skipped (listed in the report)";
    }
}
