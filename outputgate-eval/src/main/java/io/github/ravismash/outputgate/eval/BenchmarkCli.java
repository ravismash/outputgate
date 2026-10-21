package io.github.ravismash.outputgate.eval;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The benchmark from the command line, over the documents and the answers an {@link EvalRun}
 * recorded for them (see {@link BenchmarkFolder}). Neither step calls a model:
 *
 * <pre>
 * corrupt DOCUMENTS RUN [--task fcc] [--count 50] [--seed 42]
 *         writes RUN/corrupted/ from the answers the full contract accepts
 * report  DOCUMENTS RUN [--task fcc]
 *         scores the answers and the corrupted set three ways; writes RUN/benchmark.md and .html
 * </pre>
 */
public final class BenchmarkCli {

    static final String USAGE = """
            usage: benchmark corrupt DOCUMENTS RUN [--task fcc] [--count 50] [--seed 42]
                   benchmark report  DOCUMENTS RUN [--task fcc]
            RUN is an EvalRun output folder (results.jsonl), e.g. from runFccOllama.""";

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
        BenchmarkFolder folder = new BenchmarkFolder(Path.of(args[1]).toAbsolutePath(), Path.of(args[2]).toAbsolutePath());
        BenchmarkTask task = BenchmarkTask.byName(opts.getOrDefault("task", BenchmarkTask.FCC.name()));
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

    private static String skipped(int n) {
        return n == 0 ? "" : "; " + n + " skipped (listed in the report)";
    }
}
