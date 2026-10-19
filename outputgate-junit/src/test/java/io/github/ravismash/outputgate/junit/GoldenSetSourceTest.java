package io.github.ravismash.outputgate.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/** Runs {@link GoldenSample} through the JUnit engine and reads the report. */
class GoldenSetSourceTest {

    @Test
    @DisplayName("R12: @GoldenSetSource gives one named, passing test per golden case")
    void oneNamedTestPerCase() {
        Events tests = EngineTestKit.engine("junit-jupiter")
                .selectors(selectMethod(GoldenSample.class, "invoice", GoldenCase.class.getName()))
                .execute()
                .testEvents();
        assertEquals(4, tests.succeeded().count(), () -> tests.failed().list().toString());
        assertEquals(0, tests.failed().count());
        List<String> names = tests.succeeded().stream()
                .map(e -> e.getTestDescriptor().getDisplayName())
                .toList();
        assertEquals(List.of("[1] clean invoice", "[2] clean invoice with evidence", "[3] dropped line item",
                "[4] chatty prose"), names);
    }
}
