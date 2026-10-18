package io.github.ravismash.outputgate;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read access to the numbers in an output, for {@link MathRule}. Paths use the same
 * syntax as the rest of {@link MathGate} and read through evidence objects. A missing
 * or non-numeric value fails the rule with that path, so a rule never passes by accident.
 */
public interface Numbers {

    /**
     * The one number at {@code path}.
     *
     * @param path a path that resolves to exactly one value
     * @return the number
     */
    BigDecimal get(String path);

    /**
     * Every number at {@code path}, in document order.
     *
     * @param path a path, usually with {@code [*]}
     * @return the numbers
     */
    List<BigDecimal> all(String path);
}
