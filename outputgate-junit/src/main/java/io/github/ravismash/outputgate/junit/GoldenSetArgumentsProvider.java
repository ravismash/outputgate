package io.github.ravismash.outputgate.junit;

import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

/** Backs {@link GoldenSetSource}: one named argument per golden case. */
final class GoldenSetArgumentsProvider implements ArgumentsProvider, AnnotationConsumer<GoldenSetSource> {

    private String resource;

    @Override
    public void accept(GoldenSetSource annotation) {
        this.resource = annotation.value();
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return GoldenSet.load(resource).stream().map(c -> Arguments.of(Named.of(c.name(), c)));
    }
}
