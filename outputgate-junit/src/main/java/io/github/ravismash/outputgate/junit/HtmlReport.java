package io.github.ravismash.outputgate.junit;

import io.github.ravismash.outputgate.GateResult;
import io.github.ravismash.outputgate.Violation;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Renders a {@link GoldenRun} as one self-contained HTML page (R14): no scripts, no links. */
final class HtmlReport {

    private HtmlReport() {}

    static String render(GoldenRun run) {
        Set<String> gates = new LinkedHashSet<>();
        for (GoldenRun.CaseResult c : run.cases()) {
            c.verdict().results().forEach(r -> gates.add(r.gate()));
        }
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(esc(run.name())).append(" — outputgate golden run</title>")
                .append("<style>")
                .append("body{font:14px/1.45 -apple-system,Segoe UI,Helvetica,Arial,sans-serif;margin:24px;color:#1b1f24;background:#fff}")
                .append("h1{font-size:20px;margin:0 0 4px}.sub{color:#5b6470;margin:0 0 16px}")
                .append("table{border-collapse:collapse;width:100%}th,td{border-bottom:1px solid #d9dde3;padding:6px 8px;text-align:left;vertical-align:top}")
                .append("th{font-size:12px;text-transform:uppercase;color:#5b6470}td.t{text-align:center}")
                .append(".ok{color:#2f7d32;font-weight:600}.bad{color:#b4441f;font-weight:600}.na{color:#9aa1ab}")
                .append("tr.v td{border-bottom:2px solid #d9dde3;padding-top:0;color:#3b424b;font-size:13px}")
                .append("ul{margin:0;padding-left:18px}pre{white-space:pre-wrap;margin:4px 0 0;font-size:12px}")
                .append("@media(prefers-color-scheme:dark){body{background:#0e1116;color:#e6e8eb}th,.sub{color:#9aa1ab}th,td{border-color:#2a3038}tr.v td{color:#c9cdd3}}")
                .append("</style></head><body>\n");
        h.append("<h1>").append(esc(run.name())).append("</h1>")
                .append("<p class=\"sub\">pass rate <b>").append(esc(run.summary())).append("</b> · ")
                .append(esc(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .append("</p>\n<table><tr><th>#</th><th>case</th><th>expected</th>");
        for (String g : gates) {
            h.append("<th>").append(esc(g)).append("</th>");
        }
        h.append("<th>result</th></tr>\n");
        int i = 0;
        for (GoldenRun.CaseResult c : run.cases()) {
            i++;
            h.append("<tr><td>").append(i).append("</td><td>").append(esc(c.golden().name())).append("</td><td>")
                    .append(esc(expected(c.golden()))).append("</td>");
            for (String g : gates) {
                Optional<GateResult> r = c.verdict().results().stream().filter(x -> x.gate().equals(g)).findFirst();
                h.append("<td class=\"t\">")
                        .append(r.isEmpty() ? "<span class=\"na\">–</span>"
                                : r.get().passed() ? "<span class=\"ok\">✓</span>" : "<span class=\"bad\">✗</span>")
                        .append("</td>");
            }
            h.append("<td>").append(c.matched() ? "<span class=\"ok\">✓ matched</span>" : "<span class=\"bad\">✗ mismatch</span>")
                    .append("</td></tr>\n");
            if (!c.verdict().violations().isEmpty() || !c.matched()) {
                h.append("<tr class=\"v\"><td></td><td colspan=\"").append(gates.size() + 3).append("\">");
                if (!c.verdict().violations().isEmpty()) {
                    h.append("<ul>");
                    for (Violation v : c.verdict().violations()) {
                        h.append("<li>[").append(esc(v.gate())).append("] ")
                                .append(esc(v.path().isEmpty() ? "(whole output)" : v.path()))
                                .append(": ").append(esc(v.message())).append("</li>");
                    }
                    h.append("</ul>");
                }
                if (!c.matched()) {
                    h.append("<pre class=\"bad\">").append(esc(c.problem())).append("</pre>");
                }
                h.append("</td></tr>\n");
            }
        }
        h.append("</table>\n</body></html>\n");
        return h.toString();
    }

    private static String expected(GoldenCase c) {
        if (!c.mustReject()) {
            return "pass";
        }
        return "*".equals(c.expectReject()) ? "reject (any gate)" : "reject by " + c.expectReject();
    }

    static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(ch);
            }
        }
        return b.toString();
    }
}
