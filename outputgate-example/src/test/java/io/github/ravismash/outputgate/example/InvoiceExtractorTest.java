package io.github.ravismash.outputgate.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.BudgetGate;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.FakeLlmClient;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.Usage;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R10 acceptance: the example runs fully offline, with no API key. */
class InvoiceExtractorTest {

    private static String cleanOutput() throws IOException {
        try (InputStream in = InvoiceExtractorTest.class.getClassLoader().getResourceAsStream("golden/clean-output.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("R10: extract → contract, offline with FakeLlmClient, passes")
    void offlineExtractionPasses() throws IOException {
        FakeLlmClient fake = FakeLlmClient.replying(cleanOutput());
        LlmOutput out = new InvoiceExtractor(fake).extract(InvoiceExample.document());
        LlmAssert.assertThat(out).withContext(InvoiceExample.context()).passes(InvoiceExample.contract());
    }

    @Test
    @DisplayName("R10: the prompt carries every page of the document, numbered")
    void promptHasThePages() throws IOException {
        FakeLlmClient fake = FakeLlmClient.replying(cleanOutput());
        new InvoiceExtractor(fake).extract(InvoiceExample.document());
        String prompt = fake.prompts().get(0);
        assertEquals(1, fake.prompts().size());
        assertTrue(prompt.contains("--- page 1 ---") && prompt.contains("--- page 2 ---"), prompt);
        assertTrue(prompt.contains("Invoice No: INV-2041") && prompt.contains("Total due (INR)"), prompt);
        assertTrue(prompt.contains("source_span"), "the prompt asks for evidence objects");
    }

    @Test
    @DisplayName("R7 offline: the same output fails a budget it is over")
    void budgetWithFakeUsage() throws IOException {
        FakeLlmClient fake = FakeLlmClient.replying(cleanOutput())
                .withUsage(Usage.of(new BigDecimal("0.03"), Duration.ofMillis(1200), 420));
        LlmOutput out = new InvoiceExtractor(fake).extract(InvoiceExample.document());
        Contract budget = Contract.builder().gate(BudgetGate.builder().maxCost(0.02).build()).build();
        LlmAssert.assertThat(out).isRejectedBy(budget).byGate("budget");
    }

    @Test
    @DisplayName("the synthetic document has two pages")
    void documentPages() {
        assertEquals(2, InvoiceExample.document().pages().size());
        assertTrue(InvoiceExample.document().pages().get(1).contains("Subtotal"));
    }
}
