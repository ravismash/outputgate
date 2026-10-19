package io.github.ravismash.outputgate.eval;

import io.github.ravismash.outputgate.SourceDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads a PDF's text layer page by page into a {@link SourceDocument} for grounding.
 * Text is sorted by position on the page, which keeps table rows (label, amount) together.
 */
public final class PdfText {

    private PdfText() {}

    /**
     * @param pdf a PDF file with a text layer
     * @return one page of text per PDF page, in order
     * @throws IllegalArgumentException if the file is missing, not a PDF, or has no text at all (e.g. a scan)
     */
    public static SourceDocument load(Path pdf) {
        if (pdf == null || !Files.isRegularFile(pdf)) {
            throw new IllegalArgumentException("no such PDF: " + pdf);
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(stripper.getText(doc).strip());
            }
            if (pages.stream().allMatch(String::isBlank)) {
                throw new IllegalArgumentException(pdf.getFileName() + " has no text layer (scanned?); it can't be grounded");
            }
            return new SourceDocument(pages);
        } catch (IOException e) {
            throw new IllegalArgumentException("not a readable PDF: " + pdf.getFileName() + ": " + e.getMessage(), e);
        }
    }
}
