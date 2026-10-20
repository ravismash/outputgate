package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.Gate;
import io.github.ravismash.outputgate.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Catches an agency that is dropped or is the wrong party, which grounding alone can't: a
 * dropped agency has no span to check, and a rep firm's name is real text on the page.
 * On these invoices an agency commission is only charged when an agency placed the order, so
 * <ul>
 *   <li>an agency-commission deduction with agency null or missing is a violation;</li>
 *   <li>an agency that is the advertiser's own name is a violation;</li>
 *   <li>an agency named like a rep firm ("CoxReps", "... Rep Firm") is a violation.</li>
 * </ul>
 * Fails closed: an invoice that charges agency commission without printing an agency is
 * rejected, which is the safe direction.
 */
final class AgencyGate implements Gate {

    static final String NAME = "agency";

    private static final Pattern AGENCY_COMMISSION = Pattern.compile("(?i)\\bagency\\b.*\\bcomm");
    private static final Pattern REP_FIRM = Pattern.compile("(?i)(reps|\\brep firm|\\brepresentatives?)\\b");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Violation> check(JsonNode output, EvalContext context) {
        List<Violation> out = new ArrayList<>();
        String agency = text(output.path("agency"));
        boolean commission = false;
        for (JsonNode d : output.path("deductions")) {
            String label = text(d.path("label"));
            if (label != null && AGENCY_COMMISSION.matcher(label).find()) {
                commission = true;
            }
        }
        if (agency == null) {
            if (commission) {
                out.add(new Violation(NAME, "agency",
                        "an agency commission is deducted, so an agency placed the order, but agency is null"));
            }
            return out;
        }
        String advertiser = text(output.path("advertiser"));
        if (advertiser != null && normalize(agency).equals(normalize(advertiser))) {
            out.add(new Violation(NAME, "agency", "agency \"" + agency + "\" is the advertiser's own name"));
        }
        if (REP_FIRM.matcher(agency).find()) {
            out.add(new Violation(NAME, "agency", "agency \"" + agency + "\" looks like the station's rep firm, not an agency"));
        }
        return out;
    }

    /** The text of a field, read through an evidence object; null when missing, null or blank. */
    private static String text(JsonNode node) {
        JsonNode n = node.isObject() && node.has("value") ? node.get("value") : node;
        return n.isTextual() && !n.asText().isBlank() ? n.asText() : null;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
