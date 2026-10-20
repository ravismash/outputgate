package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.SchemaGate;
import io.github.ravismash.outputgate.SourceDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the benchmark measures for one dataset: the full contract, the schema the schema-only arm
 * uses, and the prompt {@code record} sends. The corruption generator and the report only need
 * these, so another dataset (CORD, SROIE) is one more task, not a new harness.
 *
 * @param name how the command line names it
 * @param schemaResource the JSON Schema on the classpath; the schema-only arm and the missing-field corruption read it
 * @param contract builds the full contract
 * @param prompt builds the extraction prompt for one document
 */
public record BenchmarkTask(String name, String schemaResource, Supplier<Contract> contract,
        Function<SourceDocument, String> prompt) {

    /** Broadcast advertising invoices (the FCC set in amazon/ConfBench), see {@link FccInvoice}. */
    public static final BenchmarkTask FCC =
            new BenchmarkTask("fcc", "schemas/fcc-invoice.schema.json", FccInvoice::contract, FccInvoice::prompt);

    private static final Map<String, BenchmarkTask> BUILT_IN = Map.of(FCC.name(), FCC);

    /** @param name a built-in task's name @return the task */
    public static BenchmarkTask byName(String name) {
        BenchmarkTask task = BUILT_IN.get(name);
        if (task == null) {
            throw new IllegalArgumentException("unknown task " + name + "; built in: " + BUILT_IN.keySet());
        }
        return task;
    }

    /** @return a contract with only the task's schema */
    public Contract schemaOnly() {
        return Contract.builder().gate(SchemaGate.fromClasspath(schemaResource)).build();
    }

    /** @return the top-level fields the schema lists as required */
    public List<String> requiredFields() {
        // The same loader SchemaGate.fromClasspath uses, so both read the same schema.
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = BenchmarkTask.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(schemaResource)) {
            if (in == null) {
                throw new IllegalArgumentException("no schema on the classpath at " + schemaResource);
            }
            List<String> required = new ArrayList<>();
            for (JsonNode field : Corruption.MAPPER.readTree(in).path("required")) {
                required.add(field.asText());
            }
            return required;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
