package io.github.ravismash.outputgate.eval;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Two-page invoices in the style of the FCC set, with different numbers each; nothing real is checked in. */
final class SyntheticFcc {

    private SyntheticFcc() {}

    static BigDecimal gross(int i) {
        return new BigDecimal("1000.00").add(new BigDecimal(i * 1137 + ".50"));
    }

    static BigDecimal agency(int i) {
        return gross(i).multiply(new BigDecimal("0.15")).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    static BigDecimal rep(int i) {
        return new BigDecimal(100 + i * 7 + ".25");
    }

    static BigDecimal net(int i) {
        return gross(i).subtract(agency(i)).subtract(rep(i));
    }

    static List<List<String>> pages(int i) {
        return List.of(
                List.of("WXYZ-TV", "INVOICE", "Invoice # 9001" + i + "-1   Invoice Date 10/2" + i + "/12",
                        "Advertiser: Example Committee " + i, "Agency: Example Media Group"),
                List.of("AIRTIME TOTAL : $ " + gross(i), "Agency Commission : $ " + agency(i),
                        "Rep Commission : $ " + rep(i), "NET TOTAL: : $ " + net(i)));
    }

    static Path pdf(Path dir, int i) throws IOException {
        Path file = dir.resolve("inv-" + i + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            for (List<String> lines : pages(i)) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream s = new PDPageContentStream(doc, page)) {
                    s.beginText();
                    s.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    s.newLineAtOffset(72, 720);
                    for (String line : lines) {
                        s.showText(line);
                        s.newLineAtOffset(0, -16);
                    }
                    s.endText();
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    static String ev(String value, int page, String text) {
        return "{\"value\": " + value + ", \"source_span\": {\"page\": " + page + ", \"text\": \"" + text + "\"}}";
    }

    /** A faithful extraction; with {@code dropRep} the rep commission is left out, as the dataset's own ground truth does. */
    static String output(int i, boolean dropRep) {
        String agency = "{\"label\": \"Agency Commission\", \"amount\": " + ev(agency(i).toPlainString(), 2, "Agency Commission : $ " + agency(i)) + "}";
        String rep = "{\"label\": \"Rep Commission\", \"amount\": " + ev(rep(i).toPlainString(), 2, "Rep Commission : $ " + rep(i)) + "}";
        return "{\"invoice_number\": " + ev("\"9001" + i + "-1\"", 1, "Invoice # 9001" + i + "-1")
                + ", \"invoice_date\": " + ev("\"2012-10-2" + i + "\"", 1, "Invoice Date 10/2" + i + "/12")
                + ", \"advertiser\": " + ev("\"Example Committee " + i + "\"", 1, "Advertiser: Example Committee " + i)
                + ", \"agency\": " + ev("\"Example Media Group\"", 1, "Agency: Example Media Group")
                + ", \"gross_total\": " + ev(gross(i).toPlainString(), 2, "AIRTIME TOTAL : $ " + gross(i))
                + ", \"deductions\": [" + agency + (dropRep ? "" : ", " + rep) + "]"
                + ", \"net_amount_due\": " + ev(net(i).toPlainString(), 2, "NET TOTAL: : $ " + net(i)) + "}";
    }
}
