package io.github.ravismash.outputgate;

/**
 * Folds typographic variants of a character to the plain form a model types, for
 * {@link ProvenanceGate.Builder#ignoringTypography()}. Each mapping is one character to the
 * character (or letters) it prints as; no letter or digit is ever changed into a different one.
 */
final class Typography {

    private Typography() {}

    static String fold(String s) {
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(c -> append(out, c));
        return out.toString();
    }

    private static void append(StringBuilder out, int c) {
        switch (c) {
            // Ligatures: what PDF text extraction often leaves in place of the letters.
            case 0xFB00 -> out.append("ff");
            case 0xFB01 -> out.append("fi");
            case 0xFB02 -> out.append("fl");
            case 0xFB03 -> out.append("ffi");
            case 0xFB04 -> out.append("ffl");
            case 0xFB05, 0xFB06 -> out.append("st");
            // Curly quotes and primes.
            case 0x2018, 0x2019, 0x201A, 0x201B, 0x2032 -> out.append('\'');
            case 0x201C, 0x201D, 0x201E, 0x201F, 0x2033 -> out.append('"');
            case 0x2026 -> out.append("...");
            // No-break, fixed-width and ideographic spaces.
            case 0x00A0, 0x2007, 0x2009, 0x202F, 0x205F, 0x3000 -> out.append(' ');
            // Soft hyphen, zero-width spaces and joiners, BOM: they print as nothing.
            case 0x00AD, 0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF -> { }
            default -> {
                if (c >= 0xFF01 && c <= 0xFF5E) {
                    out.append((char) (c - 0xFEE0)); // full-width ASCII
                } else if (c >= 0x2000 && c <= 0x200A) {
                    out.append(' ');
                } else if (c > '9' && Character.getType(c) == Character.DECIMAL_DIGIT_NUMBER) {
                    out.append((char) ('0' + Character.digit(c, 10)));
                } else {
                    out.appendCodePoint(c);
                }
            }
        }
    }
}
