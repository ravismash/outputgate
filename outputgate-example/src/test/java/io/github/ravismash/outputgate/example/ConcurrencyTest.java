package io.github.ravismash.outputgate.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.junit.GoldenCase;
import io.github.ravismash.outputgate.junit.GoldenSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One contract shared by many threads, as in a parallel test run or a server. */
class ConcurrencyTest {

    @Test
    @DisplayName("one contract evaluated from 16 threads gives exactly the sequential verdicts")
    void sharedContractIsThreadSafe() throws Exception {
        Contract contract = InvoiceExample.contract();
        EvalContext context = InvoiceExample.context();
        List<GoldenCase> cases = GoldenSet.load("golden/invoices.jsonl");
        List<Verdict> expected = cases.stream().map(c -> contract.evaluate(LlmOutput.of(c.output()), context)).toList();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int round = 0; round < 200; round++) {
                for (int i = 0; i < cases.size(); i++) {
                    int index = i;
                    futures.add(pool.submit(() -> {
                        Verdict v = contract.evaluate(LlmOutput.of(cases.get(index).output()), context);
                        return v.equals(expected.get(index)) ? 0 : 1;
                    }));
                }
            }
            int mismatches = 0;
            for (Future<Integer> f : futures) {
                mismatches += f.get();
            }
            assertEquals(0, mismatches, "verdicts that differed from the sequential run, out of " + futures.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
