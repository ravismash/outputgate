package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** One deterministic check on a parsed model output. A gate never calls a model. */
public interface Gate {

    /** Stable name shown in verdicts and reports. */
    String name();

    /**
     * Checks the output.
     *
     * @return the violations found; empty when the output passes this gate
     */
    List<Violation> check(JsonNode output, EvalContext context);
}
