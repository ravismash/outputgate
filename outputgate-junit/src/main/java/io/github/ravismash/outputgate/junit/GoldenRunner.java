package io.github.ravismash.outputgate.junit;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a whole golden set against a contract (R12) and writes a self-contained HTML
 * report for the run (R14). A case matches when it behaves as its golden line says.
 *
 * <pre>{@code
 * GoldenRun run = GoldenRunner.of(contract).run("golden/invoices.jsonl");
 * run.assertPassRateAtLeast(0.95);
 * }</pre>
 */
public final class GoldenRunner {

    /** Where reports go unless {@link #reportDir(Path)} says otherwise. */
    public static final Path DEFAULT_REPORT_DIR = Path.of("build", "outputgate");

    private final Contract contract;
    private Path reportDir = DEFAULT_REPORT_DIR;
    private EvalContext context = EvalContext.empty();

    private GoldenRunner(Contract contract) {
        this.contract = contract;
    }

    /** @param contract the contract every case is checked against @return a runner */
    public static GoldenRunner of(Contract contract) {
        if (contract == null) {
            throw new IllegalArgumentException("contract is null");
        }
        return new GoldenRunner(contract);
    }

    /** @param dir directory for the HTML report @return this runner */
    public GoldenRunner reportDir(Path dir) {
        if (dir == null) {
            throw new IllegalArgumentException("report dir is null");
        }
        this.reportDir = dir;
        return this;
    }

    /** @param context e.g. a source document for grounding @return this runner */
    public GoldenRunner withContext(EvalContext context) {
        this.context = context == null ? EvalContext.empty() : context;
        return this;
    }

    /** @return the directory the HTML report will be written to */
    public Path reportDir() {
        return reportDir;
    }

    /**
     * Checks every case and writes {@code <set>.html} into the report directory.
     *
     * @param resource the JSONL golden set on the classpath
     * @return the run
     */
    public GoldenRun run(String resource) {
        List<GoldenRun.CaseResult> results = new ArrayList<>();
        for (GoldenCase c : GoldenSet.load(resource)) {
            LlmAssert.Outcome o = LlmAssert.evaluate(c, contract, context);
            results.add(new GoldenRun.CaseResult(c, o.verdict(), o.problem() == null, o.problem()));
        }
        String name = setName(resource);
        Path report = reportDir.resolve(name + ".html");
        GoldenRun run = new GoldenRun(name, results, report);
        try {
            Files.createDirectories(reportDir);
            Files.writeString(report, HtmlReport.render(run), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write report " + report, e);
        }
        return run;
    }

    private static String setName(String resource) {
        String file = resource.substring(resource.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }
}
