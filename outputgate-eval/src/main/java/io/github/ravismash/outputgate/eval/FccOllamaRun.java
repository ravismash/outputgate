package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ravismash.outputgate.TokenPricing;
import io.github.ravismash.outputgate.ollama.OllamaLlmClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Day 12 dry run: the FCC invoice contract over a folder of PDFs, with a local Ollama model.
 * Args: pdfFolder outFolder model contextSize. If the folder has the dataset's
 * ground-truth.json, also writes ground-truth.md comparing gross and net.
 */
public final class FccOllamaRun {

    private FccOllamaRun() {}

    /** @param args pdfFolder outFolder model contextSize @throws IOException on file errors */
    public static void main(String[] args) throws IOException {
        Path pdfs = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String model = args[2];
        int ctx = Integer.parseInt(args[3]);
        OllamaLlmClient client = OllamaLlmClient.builder()
                .model(model).temperature(0.0).seed(42).contextSize(ctx).format(FccInvoice.outputSchema())
                .timeout(Duration.ofMinutes(15)).pricing(TokenPricing.FREE).build();
        List<EvalRun.Result> results = EvalRun.run(pdfs, client, FccInvoice.contract(), FccInvoice::prompt, out,
                model + " (local Ollama, temperature 0, seed 42, num_ctx " + ctx + ", format: FCC output schema)");
        Path gt = pdfs.resolve("ground-truth.json");
        if (Files.exists(gt)) {
            Files.writeString(out.resolve("ground-truth.md"), compare(results, new ObjectMapper().readTree(gt.toFile())));
        }
        System.out.println("wrote " + out.resolve("summary.md"));
    }

    /** Extracted gross and net next to the dataset's GrossTotal and NetAmountDue (the dataset omits rep commissions). */
    static String compare(List<EvalRun.Result> results, JsonNode gt) {
        ObjectMapper json = new ObjectMapper();
        StringBuilder s = new StringBuilder("# Extracted vs the dataset's ground truth\n\n"
                + "The dataset's ground truth is not always right (it omits rep-firm commissions), so a mismatch is a lead, "
                + "not a verdict.\n\n| file | outcome | gross (model) | gross (dataset) | net (model) | net (dataset) |\n|---|---|---|---|---|---|\n");
        int grossSame = 0;
        int netSame = 0;
        int compared = 0;
        for (EvalRun.Result r : results) {
            JsonNode truth = gt.path(r.file());
            String mg = "";
            String mn = "";
            if (r.output() != null) {
                try {
                    JsonNode o = json.readTree(r.output());
                    mg = value(o.path("gross_total"));
                    mn = value(o.path("net_amount_due"));
                } catch (IOException e) {
                    mg = "(not JSON)";
                }
            }
            String tg = value(truth.path("GrossTotal"));
            String tn = value(truth.path("NetAmountDue"));
            if (!mg.isEmpty() && !tg.isEmpty() && !mg.startsWith("(")) {
                compared++;
                if (same(mg, tg)) {
                    grossSame++;
                }
                if (same(mn, tn)) {
                    netSame++;
                }
            }
            s.append("| ").append(r.file(), 0, Math.min(8, r.file().length())).append(" | ").append(r.outcome())
                    .append(" | ").append(mg).append(" | ").append(tg).append(" | ").append(mn).append(" | ").append(tn).append(" |\n");
        }
        s.append("\nCompared: ").append(compared).append(" · gross equal: ").append(grossSame)
                .append(" · net equal: ").append(netSame).append('\n');
        return s.toString();
    }

    private static String value(JsonNode n) {
        JsonNode v = n.isObject() && n.has("value") ? n.get("value") : n;
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }

    private static boolean same(String a, String b) {
        try {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
