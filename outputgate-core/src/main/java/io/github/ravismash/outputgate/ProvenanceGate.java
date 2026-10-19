package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Requires fields to carry evidence, {@code {"value": ..., "source_span": {"page": n, "text": "..."}}}
 * (R6). With grounding on, each span's text must occur in the source document from the
 * {@link EvalContext}, compared literally. A missing field, a bare value or a malformed span
 * is a violation, never a pass.
 */
public final class ProvenanceGate implements Gate {

    private static final String NAME = "provenance";

    private final List<String> paths;
    private final boolean grounded;

    private ProvenanceGate(List<String> paths, boolean grounded) {
        this.paths = List.copyOf(paths);
        this.grounded = grounded;
    }

    /** Starts a new provenance gate. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Violation> check(JsonNode output, EvalContext context) {
        List<Violation> out = new ArrayList<>();
        String text = null;
        if (grounded) {
            Optional<SourceDocument> source = context.source();
            if (source.isEmpty()) {
                out.add(new Violation(NAME, "", "grounding is on but the context has no source document"));
            } else {
                text = String.join("\n", source.get().pages());
            }
        }
        for (String path : paths) {
            for (Paths.Hit hit : Paths.resolve(output, path, false)) {
                String problem = problem(hit.node());
                if (problem == null && text != null) {
                    String span = hit.node().get("source_span").get("text").asText();
                    if (!text.contains(span)) {
                        problem = "source_span.text \"" + span + "\" not found in the source document";
                    }
                }
                if (problem != null) {
                    out.add(new Violation(NAME, hit.path(), problem));
                }
            }
        }
        return out;
    }

    /** What is wrong with the evidence object, or null when it is well formed. */
    private static String problem(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return "no value";
        }
        if (!node.isObject() || !node.has("source_span")) {
            return "bare value: expected {value, source_span: {page, text}}";
        }
        if (!node.has("value")) {
            return "evidence object has no value";
        }
        JsonNode span = node.get("source_span");
        if (!span.isObject()) {
            return "source_span must be an object {page, text}";
        }
        JsonNode page = span.get("page");
        if (page == null || !page.isIntegralNumber() || page.asLong() < 1) {
            return "source_span.page must be a whole number of 1 or more";
        }
        JsonNode text = span.get("text");
        if (text == null || !text.isTextual() || text.asText().isEmpty()) {
            return "source_span.text must be non-empty text";
        }
        return null;
    }

    /** Builds a {@link ProvenanceGate}. */
    public static final class Builder {

        private final List<String> paths = new ArrayList<>();
        private boolean grounded;

        private Builder() {}

        /**
         * Fields that must carry evidence; paths with {@code [*]} cover every element.
         *
         * @param paths field paths
         * @return this builder
         */
        public Builder require(String... paths) {
            Arrays.stream(paths).map(Paths::validate).forEach(this.paths::add);
            return this;
        }

        /**
         * Also check that each span's text occurs in the source document from {@link EvalContext}.
         *
         * @return this builder
         */
        public Builder grounded() {
            this.grounded = true;
            return this;
        }

        /**
         * Builds the gate; a provenance gate that requires nothing would pass everything, so that is an error.
         *
         * @return the gate
         */
        public ProvenanceGate build() {
            if (paths.isEmpty()) {
                throw new IllegalStateException("ProvenanceGate needs at least one required field");
            }
            return new ProvenanceGate(paths, grounded);
        }
    }
}
