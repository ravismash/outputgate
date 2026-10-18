package io.github.ravismash.outputgate;

/**
 * A custom arithmetic rule for {@link MathGate}. It reads numbers from the output and
 * says whether they are consistent. It must be deterministic and must not call a model.
 */
@FunctionalInterface
public interface MathRule {

    /**
     * Checks the numbers.
     *
     * @param numbers read access to the output's numbers
     * @return true when the output satisfies the rule
     */
    boolean test(Numbers numbers);
}
