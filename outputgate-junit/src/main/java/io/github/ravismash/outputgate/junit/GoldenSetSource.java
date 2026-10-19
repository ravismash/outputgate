package io.github.ravismash.outputgate.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Feeds every case of a JSONL golden set into a {@code @ParameterizedTest}, one named
 * test per case (R12).
 *
 * <pre>{@code
 * @ParameterizedTest
 * @GoldenSetSource("golden/invoices.jsonl")
 * void invoice(GoldenCase c) {
 *     LlmAssert.assertThat(c).matches(contract);
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ArgumentsSource(GoldenSetArgumentsProvider.class)
public @interface GoldenSetSource {

    /** @return the classpath resource of the JSONL file */
    String value();
}
