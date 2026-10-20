package io.github.ravismash.outputgate.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Verdict;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

class CorruptionTest {

    static final SourceDocument DOC = SourceDocument.ofPages(
            String.join("\n", SyntheticFcc.pages(3).get(0)), String.join("\n", SyntheticFcc.pages(3).get(1)));
    static final String CLEAN = SyntheticFcc.output(3, false);
    static final List<String> REQUIRED = BenchmarkTask.FCC.requiredFields();

    @Test
    @DisplayName("the synthetic base extraction passes the full contract")
    void basePasses() {
        Verdict v = FccInvoice.contract().evaluate(CLEAN, EvalContext.empty().withSource(DOC));
        assertTrue(v.passed(), v::toString);
    }

    @ParameterizedTest
    @EnumSource(Corruption.class)
    @DisplayName("every kind applies to an FCC extraction and changes it")
    void applies(Corruption kind) {
        Optional<Corruption.Result> r = kind.apply(CLEAN, DOC, REQUIRED, new Random(1));
        assertTrue(r.isPresent(), kind.id());
        assertNotEquals(CLEAN, r.get().text());
    }

    @ParameterizedTest
    @EnumSource(Corruption.class)
    @DisplayName("the full contract rejects every kind, whichever field it lands on")
    void fullContractCatches(Corruption kind) {
        for (int seed = 0; seed < 20; seed++) {
            String bad = kind.apply(CLEAN, DOC, REQUIRED, new Random(seed)).orElseThrow().text();
            assertTrue(!FccInvoice.contract().evaluate(bad, EvalContext.empty().withSource(DOC)).passed(), kind.id() + "\n" + bad);
        }
    }

    @Test
    @DisplayName("a reformatted quote is never one the document also prints")
    void reformattedQuoteIsReallyAbsent() {
        for (int seed = 0; seed < 20; seed++) {
            Corruption.Result r = Corruption.REFORMATTED_QUOTE.apply(CLEAN, DOC, REQUIRED, new Random(seed)).orElseThrow();
            String quote = r.detail().replaceAll(".*-> \"(.*)\"$", "$1");
            assertTrue(DOC.pages().stream().noneMatch(p -> p.contains(quote)), r.detail());
        }
    }

    @Test
    @DisplayName("a kind that has nothing to work on does not apply")
    void notApplicable() {
        SourceDocument doc = SourceDocument.of("Total 5.00");
        String noLists = "{\"total\": {\"value\": 5.00, \"source_span\": {\"page\": 1, \"text\": \"Total 5.00\"}}}";
        assertTrue(Corruption.DROPPED_ITEM.apply(noLists, doc, List.of(), new Random(1)).isEmpty());
        assertTrue(Corruption.NON_ISO_DATE.apply(noLists, doc, List.of(), new Random(1)).isEmpty());
        assertTrue(Corruption.MISSING_FIELD.apply(noLists, doc, List.of("vendor"), new Random(1)).isEmpty());
        assertTrue(Corruption.APOLOGY.apply("not json", doc, List.of(), new Random(1)).isEmpty());
    }

    @Test
    @DisplayName("the same seed makes the same corruption")
    void deterministic() {
        for (Corruption kind : Corruption.values()) {
            assertEquals(kind.apply(CLEAN, DOC, REQUIRED, new Random(7)), kind.apply(CLEAN, DOC, REQUIRED, new Random(7)), kind.id());
        }
    }
}
